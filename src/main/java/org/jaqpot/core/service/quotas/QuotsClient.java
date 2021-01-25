/*
 *
 * JAQPOT Quattro
 *
 * JAQPOT Quattro and the components shipped with it, in particular:
 * (i)   JaqpotCoreServices
 * (ii)  JaqpotAlgorithmServices
 * (iii) JaqpotDB
 * (iv)  JaqpotDomain
 * (v)   JaqpotEAR
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
package org.jaqpot.core.service.quotas;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.ParseException;
import java.util.concurrent.ExecutionException;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import org.asynchttpclient.Response;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ws.rs.InternalServerErrorException;
import org.jaqpot.core.model.ErrorReport;
import org.jaqpot.core.properties.PropertyManager;
import org.jaqpot.core.service.authentication.AAService;
import org.jaqpot.core.service.exceptions.JaqpotNotAuthorizedException;
import xyz.euclia.euclia.accounts.client.models.User;
import xyz.euclia.jquots.JQuots;
import xyz.euclia.jquots.JQuotsClientFactory;
import xyz.euclia.jquots.models.CanProceed;
import xyz.euclia.jquots.serialize.Serializer;

/**
 *
 * @author pantelispanka
 */
@Startup
@Singleton
@DependsOn("PropertyManager")
public class QuotsClient {

    private static final Logger LOG = Logger.getLogger(QuotsClient.class.getName());

    @Inject
    PropertyManager propertyManager;

    @EJB
    AAService aaService;

    private JQuots quotsClient;
    private Serializer serializer;

    public QuotsClient() {

    }

    @PostConstruct
    public void Init() {

        LOG.log(Level.INFO, "Initializing QuotsClient");
        String quotsUrl = propertyManager.getPropertyOrDefault(PropertyManager.PropertyType.QUOTS_URL);
        String quotsApp = propertyManager.getPropertyOrDefault(PropertyManager.PropertyType.QUOTS_APP);
        LOG.log(Level.INFO, "Quots URL : {0}", quotsUrl);
        LOG.log(Level.INFO, "Quots App : {0}", quotsApp);
        this.quotsClient = JQuotsClientFactory
                .createNewClient(propertyManager.getPropertyOrDefault(PropertyManager.PropertyType.QUOTS_URL),
                        propertyManager.getPropertyOrDefault(PropertyManager.PropertyType.QUOTS_APP),
                        propertyManager.getPropertyOrDefault(PropertyManager.PropertyType.QUOTS_APP_SECRET),
                        new JQuotsSerializer(new ObjectMapper()));
        serializer = new JQuotsSerializer(new ObjectMapper());

    }

    public CanProceed canUserProceedSync(String userId, String usage, String size, String accessToken) throws InterruptedException, ExecutionException, InternalServerErrorException, JaqpotNotAuthorizedException, JaqpotNotAuthorizedException, JaqpotNotAuthorizedException, ParseException {
        Future<Response> proceed = this.quotsClient.canProceed(userId, usage, size);
        CanProceed cp = new CanProceed();
        User user = aaService.getUserFromSSO(accessToken);
        switch (proceed.get().getStatusCode()) {
            case 400:
                this.quotsClient.createUser(userId, user.getName(), user.getEmail());
                cp.setUserid(userId);
                cp.setProceed(true);
                break;
            case 200:
                cp = serializer.parse(proceed.get().getResponseBody(), CanProceed.class);
                break;
            default:
                ErrorReport er = serializer.parse(proceed.get().getResponseBody(), ErrorReport.class);
                throw new InternalServerErrorException(er.getMessage());
        }
        return cp;
    }

    public Future<Response> canUserProceedAsync(String userId, String usage, String size, String userName, String userEmail) throws InterruptedException, ExecutionException, InternalServerErrorException {
        Future<Response> proceed = this.quotsClient.canProceed(userId, usage, usage);
        return proceed;
    }
    
    public Serializer getSerializer(){
        return this.serializer;
    }

}
