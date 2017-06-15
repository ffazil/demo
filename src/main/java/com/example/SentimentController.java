package com.example;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.XMLOutputter;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import lombok.extern.slf4j.Slf4j;
import nu.xom.Document;
import nu.xom.Nodes;
import nu.xom.xslt.XSLTransform;
import org.apache.catalina.servlet4preview.http.HttpServletRequest;
import org.apache.commons.lang3.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author firoz
 * @since 28/04/17
 */
@Slf4j
@Controller
public class SentimentController {

    private static final int MAXIMUM_QUERY_LENGTH = 4096;
    private String defaultFormat = "pretty";

    private XSLTransform corenlpTransformer;

    @Autowired
    private StanfordCoreNLP pipeline;

    @RequestMapping(path = "/analyze", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> analyze(@RequestBody Text text, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        /*Annotation document = pipeline.process(text.getContent());
        pipeline.annotate(document);
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

        String s = null;
        for(CoreMap sentence: sentences) {
            s = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            Tree tree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
            ArrayList<Word> words = tree.yieldWords();
            int sentiment = RNNCoreAnnotations.getPredictedClass(tree);
            String partText = sentence.toString();
            log.info("{} = {}", partText, sentiment);
        }*/

        String input = text.getContent();
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);

        if (input.length() > MAXIMUM_QUERY_LENGTH) {
            return new ResponseEntity<String>(HttpStatus.NO_CONTENT);
        }

        Annotation annotation = new Annotation(input);
        pipeline.annotate(annotation);
        pipeline.jsonPrint(annotation,writer);

        writer.flush();
        return ResponseEntity.ok(out.toString());
    }

    public void addResults(javax.servlet.http.HttpServletRequest request,
                           HttpServletResponse response)
            throws ServletException, IOException
    {
        String input = request.getParameter("input");
        if (input == null) {
            return;
        }
        input = input.trim();
        if (input.equals("")) {
            return;
        }

        PrintWriter out = response.getWriter();
        if (input.length() > MAXIMUM_QUERY_LENGTH) {
            out.print("<div>This query is too long.  If you want to run very long queries, please download and use our <a href=\"http://nlp.stanford.edu/software/corenlp.shtml\">publicly released distribution</a>.</div>");
            return;
        }

        Annotation annotation = new Annotation(input);
        pipeline.annotate(annotation);

        String outputFormat = request.getParameter("outputFormat");
        if (outputFormat == null || outputFormat.trim().equals("")) {
            outputFormat = this.defaultFormat;
        }

        switch (outputFormat) {
            case "xml":
                outputXml(out, annotation);
                break;
            case "json":
                outputJson(out, annotation);
                break;
            case "conll":
                outputCoNLL(out, annotation);
                break;
            case "pretty":
                outputPretty(out, annotation);
                break;
            default:
                outputVisualise(out, annotation);
                break;
        }
    }

    public void outputVisualise(PrintWriter out, Annotation annotation)
            throws ServletException, IOException
    {
        // Note: A lot of the HTML generation in this method could/should be
        // done at a templating level, but as-of-yet I am not entirely sure how
        // this should be done in jsp. Also, a lot of the HTML is unnecessary
        // for the other outputs such as pretty print and XML.

        // Div for potential error messages when fetching the configuration.
        out.println("<div id=\"config_error\">");
        out.println("</div>");

        // Insert divs that will be used for each visualisation type.
        final int visualiserDivPxWidth = 700;
        Map<String, String> nameByAbbrv = new LinkedHashMap<>();
        nameByAbbrv.put("pos", "Part-of-Speech");
        nameByAbbrv.put("ner", "Named Entity Recognition");
        nameByAbbrv.put("coref", "Coreference");
        nameByAbbrv.put("basic_dep", "Basic Dependencies");
        //nameByAbbrv.put("collapsed_dep", "Collapsed dependencies");
        nameByAbbrv.put("collapsed_ccproc_dep",
                "Enhanced Dependencies");
        for (Map.Entry<String, String> entry : nameByAbbrv.entrySet()) {
            out.println("<h2>" + entry.getValue() + ":</h2>");
            out.println("<div id=\"" + entry.getKey() + "\" style=\"width:"
                    + visualiserDivPxWidth + "px\">");
            out.println("    <div id=\"" + entry.getKey() + "_loading\">");
            out.println("        <p>Loading...</p>");
            out.println("    </div>");
            out.println("</div>");
            out.println("");
        }

        // Time to get the XML data into HTML.
        StringWriter xmlOutput = new StringWriter();
        pipeline.xmlPrint(annotation, xmlOutput);
        xmlOutput.flush();

        // Escape the XML to be embeddable into a Javascript string.
        String escapedXml = xmlOutput.toString().replaceAll("\\r\\n|\\r|\\n", ""
        ).replace("\"", "\\\"");

        // Inject the XML results into the HTML to be retrieved by the Javascript.
        out.println("<script type=\"text/javascript\">");
        out.println("// <![CDATA[");
        out.println("    stanfordXML = \"" + escapedXml + "\";");
        out.println("// ]]>");
        out.println("</script>");

        // Relative brat installation location to CoreNLP.
        final String bratLocation = "../brat";

        // Inject the location variable, we need it in Javascript mode.
        out.println("<script type=\"text/javascript\">");
        out.println("// <![CDATA[");
        out.println("    bratLocation = \"" + bratLocation + "\";");
        out.println("    webFontURLs = [\n" +
                "        '"+ bratLocation + "/static/fonts/Astloch-Bold.ttf',\n" +
                "        '"+ bratLocation + "/static/fonts/PT_Sans-Caption-Web-Regular.ttf',\n" +
                "        '"+ bratLocation + "/static/fonts/Liberation_Sans-Regular.ttf'];");
        out.println("// ]]>");
        out.println("</script>");

        // Inject the brat stylesheet (removing this line breaks visualisation).
        out.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"" +
                bratLocation + "/style-vis.css\"/>");

        // Include the Javascript libraries necessary to run brat.
        out.println("<script type=\"text/javascript\" src=\"" + bratLocation +
                "/client/lib/head.load.min.js\"></script>");
        // Main Javascript that hooks into all that we have introduced so far.
        out.println("<script type=\"text/javascript\" src=\"brat.js\"></script>");

        // Link to brat, I hope this is okay to have here...
        out.println("<h>Visualisation provided using the " +
                "<a href=\"http://brat.nlplab.org/\">brat " +
                "visualisation/annotation software</a>.</h>");
        out.println("<br/>");
    }


    public void outputJson(PrintWriter out, Annotation annotation) throws IOException {
        outputByWriter(writer -> {
            try {
                pipeline.jsonPrint(annotation, writer);
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }, out);
    }

    public void outputXml(PrintWriter out, Annotation annotation) throws IOException {
        outputByWriter(writer -> {
            try {
                pipeline.xmlPrint(annotation, writer);
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }, out);
    }

    public void outputCoNLL(PrintWriter out, Annotation annotation) throws IOException {
        outputByWriter(writer -> {
            try {
                pipeline.conllPrint(annotation, writer);
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }, out);
    }

    public void outputPretty(PrintWriter out, Annotation annotation)
            throws ServletException
    {
        try {
            Document input = XMLOutputter.annotationToDoc(annotation, pipeline);

            Nodes output = corenlpTransformer.transform(input);
            for (int i = 0; i < output.size(); i++) {
                out.print(output.get(i).toXML());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }



    public void outputByWriter(Consumer<StringWriter> printer,
                               PrintWriter out) throws IOException {
        StringWriter output = new StringWriter();
        printer.accept(output);
        output.flush();

        String escapedXml = StringEscapeUtils.escapeHtml4(output.toString());
        String[] lines = escapedXml.split("\n");
        out.print("<div><pre>");
        for (String line : lines) {
            int numSpaces = 0;
            while (numSpaces < line.length() && line.charAt(numSpaces) == ' ') {
                out.print("&nbsp;");
                ++numSpaces;
            }
            out.print(line.substring(numSpaces));
            out.print("\n");
        }
        out.print("</pre></div>");
    }

    private String getTree(Annotation annotation){

        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences != null && ! sentences.isEmpty()) {
            CoreMap sentence = sentences.get(0);
            out.println("The keys of the first sentence's CoreMap are:");
            out.println(sentence.keySet());
            out.println();
            out.println("The first sentence is:");
            out.println(sentence.toShorterString());
            out.println();
            out.println("The first sentence tokens are:");
            for (CoreMap token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                out.println(token.toShorterString());
            }
            Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
            out.println();
            out.println("The first sentence parse tree is:");
            tree.pennPrint(out);
            out.println();
            out.println("The first sentence basic dependencies are:");
            out.println(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class).toString(SemanticGraph.OutputFormat.LIST));
            out.println("The first sentence collapsed, CC-processed dependencies are:");
            SemanticGraph graph = sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
            out.println(graph.toString(SemanticGraph.OutputFormat.LIST));

            // Access coreference. In the coreference link graph,
            // each chain stores a set of mentions that co-refer with each other,
            // along with a method for getting the most representative mention.
            // Both sentence and token offsets start at 1!
            out.println("Coreference information");
            Map<Integer, CorefChain> corefChains =
                    annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
            if (corefChains == null) { return null; }
            for (Map.Entry<Integer,CorefChain> entry: corefChains.entrySet()) {
                out.println("Chain " + entry.getKey());
                for (CorefChain.CorefMention m : entry.getValue().getMentionsInTextualOrder()) {
                    // We need to subtract one since the indices count from 1 but the Lists start from 0
                    List<CoreLabel> tokens = sentences.get(m.sentNum - 1).get(CoreAnnotations.TokensAnnotation.class);
                    // We subtract two for end: one for 0-based indexing, and one because we want last token of mention not one following.
                    out.println("  " + m + ", i.e., 0-based character offsets [" + tokens.get(m.startIndex - 1).beginPosition() +
                            ", " + tokens.get(m.endIndex - 2).endPosition() + ")");
                }
            }
            out.println();

            out.println("The first sentence overall sentiment rating is " + sentence.get(SentimentCoreAnnotations.SentimentClass.class));
        }
        IOUtils.closeIgnoringExceptions(out);
        return writer.toString();

    }


}
