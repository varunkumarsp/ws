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
package org.exoplatform.services.rest.ext.transport;

import org.exoplatform.services.rest.GenericContainerResponse;
import org.exoplatform.services.rest.impl.MultivaluedMapImpl;

import java.io.Serializable;

import javax.ws.rs.core.MultivaluedMap;

/**
 * SerialResponse can be used for transfer data (HTTP status, HTTP headers,
 * entity) from {@link GenericContainerResponse} via RMI.
 * 
 * @see SerialRequest
 * @author <a href="mailto:andrew00x@gmail.com">Andrey Parfonov</a>
 * @version $Id: $
 */
public class SerialResponse implements Serializable
{

   /**
    * Generated by Eclipse.
    */
   private static final long serialVersionUID = 7250729921392627533L;

   /**
    * HTTP status.
    */
   private int status;

   /**
    * HTTP headers.
    */
   private MultivaluedMap<String, String> headers;

   /**
    * See {@link SerialInputData}.
    */
   private SerialInputData data;

   public SerialResponse()
   {
      this.headers = new MultivaluedMapImpl();
   }

   /**
    * @return HTTP status of response
    */
   public int getStatus()
   {
      return status;
   }

   /**
    * Set HTTP status for response.
    * 
    * @param status HTTP status
    */
   public void setStatus(int status)
   {
      this.status = status;
   }

   /**
    * @return HTTP headers, also see {@link MultivaluedMap}
    */
   public MultivaluedMap<String, String> getHeaders()
   {
      return headers;
   }

   /**
    * Set HTTP header with supplied name and value, preset header with this name
    * will be overridden.
    * 
    * @param name header name
    * @param value header value
    */
   public void setHeader(String name, String value)
   {
      headers.putSingle(name, value);
   }

   /**
    * HTTP header with supplied name and value.
    * 
    * @param name header name
    * @param value header value
    */
   public void addHeader(String name, String value)
   {
      headers.add(name, value);
   }

   /**
    * @return See {@link SerialInputData}
    */
   public SerialInputData getData()
   {
      return data;
   }

   /**
    * @param data see {@link SerialInputData}
    */
   public void setData(SerialInputData data)
   {
      this.data = data;
   }

}
