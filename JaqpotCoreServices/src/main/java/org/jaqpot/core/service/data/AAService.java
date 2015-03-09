/*
 *
 * JAQPOT Quattro
 *
 * JAQPOT Quattro and the components shipped with it (web applications and beans)
 * are licenced by GPL v3 as specified hereafter. Additional components may ship
 * with some other licence as will be specified therein.
 *
 * Copyright (C) 2014-2015 KinkyDesign (Charalampos Chomenidis, Pantelis Sopasakis)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Source code:
 * The source code of JAQPOT Quattro is available on github at:
 * https://github.com/KinkyDesign/JaqpotQuattro
 * All source files of JAQPOT Quattro that are stored on github are licenced
 * with the aforementioned licence. 
 */
package org.jaqpot.core.service.data;

import java.security.GeneralSecurityException;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.jaqpot.core.data.UserHandler;
import org.jaqpot.core.model.factory.ErrorReportFactory;
import org.jaqpot.core.service.client.Util;
import org.jaqpot.core.service.dto.aa.AuthToken;
import org.jaqpot.core.service.exceptions.JaqpotNotAuthorizedException;

/**
 *
 * @author Pantelis Sopasakis
 * @author Charalampos Chomenidis
 *
 */
@Stateless
public class AAService {

    @EJB
    UserHandler userHandler;

    public AuthToken login(String username, String password) {
        try {
            Client client = Util.buildUnsecureRestClient();
            MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
            formData.putSingle("username", username);
            formData.putSingle("password", password);
            Response response = client.target("https://openam.in-silico.ch/auth/authenticate")
                    .request()
                    .post(Entity.form(formData));
            String responseValue = response.readEntity(String.class);
            response.close();
            if (response.getStatus() == 401) {
                return null;
            } else {
                AuthToken aToken = new AuthToken();
                aToken.setAuthToken(responseValue.substring(9).replaceAll("\n", ""));
                aToken.setUserName(username);
                return aToken;
            }

        } catch (GeneralSecurityException ex) {
            throw new InternalServerErrorException(ex);
        }
    }

}