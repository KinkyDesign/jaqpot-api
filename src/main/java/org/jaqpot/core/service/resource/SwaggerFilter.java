/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.service.resource;

import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderListener;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;

/**
 *
 * @author hampos
 */
@SwaggerDefinition(
        info = @Info(
                title = "Jaqpot API",
                description = "JAQPOT Quattro is the 4th version of a YAQP, a RESTful web service which can be used to train machine learning models and use them to obtain toxicological predictions for given chemical compounds or engineered nano materials. The project is written in Java8 and JEE7.",
                version = "4.0.3",
                contact = @Contact(name = "Charalampos Chomenidis", email = "hampos@me.com")
        )
)
public class SwaggerFilter implements ReaderListener {

    @Override
    public void beforeScan(Reader reader, Swagger swgr) {

    }

    @Override
    public void afterScan(Reader reader, Swagger swgr) {
        ApiKeyAuthDefinition apiKeyDefinition = new ApiKeyAuthDefinition();
        apiKeyDefinition.setName("subjectid");
        apiKeyDefinition.setIn(In.HEADER);
        swgr.addSecurityDefinition("subjectid", apiKeyDefinition);
    }

}
