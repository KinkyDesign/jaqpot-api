/*
 *
 * JAQPOT Quattro
 *
 * JAQPOT Quattro and the components shipped with it (web applications and beans)
 * are licensed by GPL v3 as specified hereafter. Additional components may ship
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
 * All source files of JAQPOT Quattro that are stored on github are licensed
 * with the aforementioned licence. 
 */
package org.jaqpot.core.service.filter.excmappers;

import javax.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.jaqpot.core.service.exceptions.JaqpotNotAuthorizedException;

/**
 *
 * @author Pantelis Sopasakis
 * @author Charalampos Chomenidis
 *
 */
@Provider
public class JaqpotNotAuthorizedExceptionMapper implements ExceptionMapper<JaqpotNotAuthorizedException> {
    
    private static final Logger LOG = Logger.getLogger(JaqpotNotAuthorizedExceptionMapper.class.getName());    

    @Override
    public Response toResponse(JaqpotNotAuthorizedException exception) {
        LOG.log(Level.FINER, "JaqpotNotAuthorizedExceptionMapper exception caught", exception);
        return Response
                .ok(exception.getError())
                .status(Response.Status.UNAUTHORIZED)
                .build();
    }

}
