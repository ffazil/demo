package com.example;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * @author firoz
 * @since 13/06/17
 */
@Controller
public class HomeController {

    @RequestMapping(path = "/", method = RequestMethod.GET)
    public String home(){
        return "/demo.html";
    }
}
