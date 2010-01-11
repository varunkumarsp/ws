/*
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.services.rest.tools;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

import org.exoplatform.services.rest.ContainerResponseWriter;
import org.exoplatform.services.rest.RequestHandler;
import org.exoplatform.services.rest.impl.ContainerRequest;
import org.exoplatform.services.rest.impl.ContainerResponse;
import org.exoplatform.services.rest.impl.EnvironmentContext;
import org.exoplatform.services.rest.impl.InputHeadersMap;
import org.exoplatform.services.rest.impl.MultivaluedMapImpl;

/**
 * This class may be useful for running test and should not be used for launching
 * services in real environment, Servlet Container for example.
 *  
 * @author <a href="mailto:andrew00x@gmail.com">Andrey Parfonov</a>
 * @version $Id$
 */
public class ResourceLauncher
{

   private final RequestHandler requestHandler;

   public ResourceLauncher(RequestHandler requestHandler)
   {
      this.requestHandler = requestHandler;
   }

   /**
    * @param method HTTP method
    * @param requestURI full requested URI
    * @param baseURI base requested URI
    * @param headers HTTP headers
    * @param data data
    * @param writer response writer
    * @param env environment context
    * @return response
    * @throws Exception if any error occurs
    */
   public ContainerResponse service(String method, String requestURI, String baseURI,
      Map<String, List<String>> headers, byte[] data, ContainerResponseWriter writer, EnvironmentContext env)
      throws Exception
   {

      if (headers == null)
         headers = new MultivaluedMapImpl();

      ByteArrayInputStream in = null;
      if (data != null)
         in = new ByteArrayInputStream(data);

      if (env == null)
         env = new EnvironmentContext();
      EnvironmentContext.setCurrent(env);
      
      ContainerRequest request =
         new ContainerRequest(method, new URI(requestURI), new URI(baseURI), in, new InputHeadersMap(headers));
      ContainerResponse response = new ContainerResponse(writer);
      requestHandler.handleRequest(request, response);
      return response;
   }

   /**
    * @param method HTTP method
    * @param requestURI full requested URI
    * @param baseURI base requested URI
    * @param headers HTTP headers
    * @param data data
    * @param env environment context
    * @return response
    * @throws Exception if any error occurs
    */
   public ContainerResponse service(String method, String requestURI, String baseURI,
      MultivaluedMap<String, String> headers, byte[] data, EnvironmentContext env) throws Exception
   {
      return service(method, requestURI, baseURI, headers, data, new DummyContainerResponseWriter(), env);
   }

}