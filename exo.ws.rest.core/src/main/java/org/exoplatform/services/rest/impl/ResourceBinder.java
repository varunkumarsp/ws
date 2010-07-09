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
package org.exoplatform.services.rest.impl;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.ApplicationContext;
import org.exoplatform.services.rest.Filter;
import org.exoplatform.services.rest.ObjectFactory;
import org.exoplatform.services.rest.ObjectModel;
import org.exoplatform.services.rest.PerRequestObjectFactory;
import org.exoplatform.services.rest.RequestFilter;
import org.exoplatform.services.rest.ResponseFilter;
import org.exoplatform.services.rest.SingletonObjectFactory;
import org.exoplatform.services.rest.impl.method.DefaultMethodInvoker;
import org.exoplatform.services.rest.impl.method.MethodInvokerFactory;
import org.exoplatform.services.rest.impl.resource.AbstractResourceDescriptorImpl;
import org.exoplatform.services.rest.impl.resource.ResourceDescriptorValidator;
import org.exoplatform.services.rest.method.MethodInvokerFilter;
import org.exoplatform.services.rest.resource.AbstractResourceDescriptor;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.rest.resource.ResourceDescriptorVisitor;
import org.exoplatform.services.rest.uri.UriPattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Path;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.RuntimeDelegate;

/**
 * Lookup for root resource eXo container components at startup and
 * register/unregister resources via specified methods.
 *
 * @see AbstractResourceDescriptor
 * @see SingletonResourceFactory
 * @author <a href="mailto:andrew00x@gmail.com">Andrey Parfonov</a>
 * @version $Id: $
 */
public class ResourceBinder
{

   /** Logger. */
   private static final Log LOG = ExoLogger.getLogger("exo.ws.rest.core.ResourceBinder");

   /** Resource's comparator. */
   protected static final Comparator<ObjectFactory<AbstractResourceDescriptor>> RESOURCE_COMPARATOR =
      new Comparator<ObjectFactory<AbstractResourceDescriptor>>()
      {
         /**
          * Compare two ResourceClass for order.
          *
          * @param o1 first ResourceClass to be compared
          * @param o2 second ResourceClass to be compared
          * @return positive , zero or negative dependent of {@link UriPattern}
          *         comparison
          * @see Comparator#compare(Object, Object)
          * @see UriPattern
          * @see UriPattern#URIPATTERN_COMPARATOR
          */
         public int compare(ObjectFactory<AbstractResourceDescriptor> o1, ObjectFactory<AbstractResourceDescriptor> o2)
         {
            return UriPattern.URIPATTERN_COMPARATOR.compare(o1.getObjectModel().getUriPattern(), o2.getObjectModel()
               .getUriPattern());
         }
      };

   /** Validator. */
   protected final ResourceDescriptorVisitor rdv = ResourceDescriptorValidator.getInstance();

   /** Amount of available root resources. */
   protected int size = 0;

   /** @see RuntimeDelegate */
   protected final RuntimeDelegate rd;

   /**
    * Producer of methods invokers. If not specified then
    * {@link DefaultMethodInvoker} will be in use.
    */
   protected final MethodInvokerFactory invokerFactory;

   /** List of all available root resources. */
   protected final List<ObjectFactory<AbstractResourceDescriptor>> rootResources =
      new ArrayList<ObjectFactory<AbstractResourceDescriptor>>();

   public ResourceBinder(ExoContainerContext containerContext) throws Exception
   {
      this(containerContext, null);
   }

   /**
    * @param containerContext eXo container context
    * @param invokerFactory method invoker producer
    * @throws Exception if can't set instance of {@link RuntimeDelegate}
    * @see MethodInvokerFactory
    */
   @SuppressWarnings("unchecked")
   public ResourceBinder(ExoContainerContext containerContext, MethodInvokerFactory invokerFactory) throws Exception
   {
      this.invokerFactory = invokerFactory;

      // Initialize RuntimeDelegate instance
      // This is first component in life cycle what needs.
      // TODO better solution to initialize RuntimeDelegate
      RuntimeDelegate.setInstance(new RuntimeDelegateImpl());
      rd = RuntimeDelegate.getInstance();

      ExoContainer container = containerContext.getContainer();

      // Lookup Applications
      List<Application> al = container.getComponentInstancesOfType(Application.class);
      for (Application a : al)
      {
         try
         {
            addApplication(a);
         }
         catch (Exception e)
         {
            LOG.error("Failed add JAX-RS application " + a.getClass().getName(), e);
         }
      }

      // Lookup all object which implements ResourceContainer interface and
      // process them to be add as root resources.
      for (Object resource : container.getComponentInstancesOfType(ResourceContainer.class))
      {
         try
         {
            addResource(resource, null);
         }
         catch (Exception e)
         {
            LOG.error("Failed add JAX-RS resource " + resource.getClass().getName(), e);
         }
      }
   }

   /**
    * @param application Application
    * @see Application
    */
   @SuppressWarnings("unchecked")
   public void addApplication(Application application)
   {
      ProviderBinder providers = ProviderBinder.getInstance();
      for (Object obj : application.getSingletons())
      {
         if (obj.getClass().getAnnotation(Provider.class) != null)
         {
            // singleton provider
            if (obj instanceof ContextResolver)
            {
               providers.addContextResolver((ContextResolver)obj);
            }
            if (obj instanceof ExceptionMapper)
            {
               providers.addExceptionMapper((ExceptionMapper)obj);
            }
            if (obj instanceof MessageBodyReader)
            {
               providers.addMessageBodyReader((MessageBodyReader)obj);
            }
            if (obj instanceof MessageBodyWriter)
            {
               providers.addMessageBodyWriter((MessageBodyWriter)obj);
            }
         }
         else if (obj.getClass().getAnnotation(Filter.class) != null)
         {
            // singleton filter
            if (obj instanceof MethodInvokerFilter)
            {
               providers.addMethodInvokerFilter((MethodInvokerFilter)obj);
            }
            if (obj instanceof RequestFilter)
            {
               providers.addRequestFilter((RequestFilter)obj);
            }
            if (obj instanceof ResponseFilter)
            {
               providers.addResponseFilter((ResponseFilter)obj);
            }
         }
         else
         {
            addResource(obj, null); // singleton resource
         }
      }
      for (Class clazz : application.getClasses())
      {
         if (clazz.getAnnotation(Provider.class) != null)
         {
            // per-request provider
            if (ContextResolver.class.isAssignableFrom(clazz))
            {
               providers.addContextResolver(clazz);
            }
            if (ExceptionMapper.class.isAssignableFrom(clazz))
            {
               providers.addExceptionMapper(clazz);
            }
            if (MessageBodyReader.class.isAssignableFrom(clazz))
            {
               providers.addMessageBodyReader(clazz);
            }
            if (MessageBodyWriter.class.isAssignableFrom(clazz))
            {
               providers.addMessageBodyWriter(clazz);
            }
         }
         else if (clazz.getAnnotation(Filter.class) != null)
         {
            // per-request filter
            if (MethodInvokerFilter.class.isAssignableFrom(clazz))
            {
               providers.addMethodInvokerFilter(clazz);
            }
            if (RequestFilter.class.isAssignableFrom(clazz))
            {
               providers.addRequestFilter(clazz);
            }
            if (ResponseFilter.class.isAssignableFrom(clazz))
            {
               providers.addResponseFilter(clazz);
            }
         }
         else
         {
            addResource(clazz, null); // per-request resource
         }
      }
   }

   /**
    * Register supplied class as per-request root resource if it has valid
    * JAX-RS annotations and no one resource with the same UriPattern already
    * registered.
    *
    * @param resourceClass class of candidate to be root resource
    * @param properties optional resource properties. It may contains additional
    *        info about resource, e.g. description of resource, its
    *        responsibility, etc. This info can be retrieved
    *        {@link ObjectModel#getProperties()}. This parameter may be
    *        <code>null</code>
    * @throws ResourcePublicationException if resource can't be published
    *         because to:
    *         <ul>
    *         <li>&#64javax.ws.rs.Path annotation is missing</li>
    *         <li>resource has not any method with JAX-RS annotations</li>
    *         <li>JAX-RS annotations are ambiguous or invalid</li>
    *         <li>resource with the sane {@link UriPattern} already registered</li>
    *         </ul>
    * @see ObjectModel#getProperties()
    * @see ObjectModel#getProperty(String)
    */
   public void addResource(final Class<?> resourceClass, MultivaluedMap<String, String> properties)
   {
      Path path = resourceClass.getAnnotation(Path.class);
      if (path == null)
      {
         throw new ResourcePublicationException("Resource class " + resourceClass.getName()
            + " it is not root resource. " + "Path annotation javax.ws.rs.Path is not specified for this class.");
      }
      try
      {
         AbstractResourceDescriptor descriptor = new AbstractResourceDescriptorImpl(resourceClass, invokerFactory);
         // validate AbstractResourceDescriptor
         descriptor.accept(rdv);
         if (properties != null)
            descriptor.getProperties().putAll(properties);
         addResource(new PerRequestObjectFactory<AbstractResourceDescriptor>(descriptor));
      }
      catch (Exception e)
      {
         throw new ResourcePublicationException(e.getMessage());
      }
   }

   /**
    * Register supplied Object as singleton root resource if it has valid JAX-RS
    * annotations and no one resource with the same UriPattern already
    * registered.
    *
    * @param resource candidate to be root resource
    * @param properties optional resource properties. It may contains additional
    *        info about resource, e.g. description of resource, its
    *        responsibility, etc. This info can be retrieved
    *        {@link ObjectModel#getProperties()}. This parameter may be
    *        <code>null</code>
    * @throws ResourcePublicationException if resource can't be published
    *         because to:
    *         <ul>
    *         <li>&#64javax.ws.rs.Path annotation is missing</li>
    *         <li>resource has not any method with JAX-RS annotations</li>
    *         <li>JAX-RS annotations are ambiguous or invalid</li>
    *         <li>resource with the sane {@link UriPattern} already registered</li>
    *         </ul>
    * @see ObjectModel#getProperties()
    * @see ObjectModel#getProperty(String)
    */
   public void addResource(final Object resource, MultivaluedMap<String, String> properties)
   {
      Path path = resource.getClass().getAnnotation(Path.class);
      if (path == null)
      {
         throw new ResourcePublicationException("Resource class " + resource.getClass().getName()
            + " it is not root resource. " + "Path annotation javax.ws.rs.Path is not specified for this class.");
      }
      try
      {
         AbstractResourceDescriptor descriptor = new AbstractResourceDescriptorImpl(resource, invokerFactory);
         // validate AbstractResourceDescriptor
         descriptor.accept(rdv);
         if (properties != null)
            descriptor.getProperties().putAll(properties);
         addResource(new SingletonObjectFactory<AbstractResourceDescriptor>(descriptor, resource));
      }
      catch (Exception e)
      {
         throw new ResourcePublicationException(e.getMessage());
      }
   }

   /**
    * Register supplied root resource if no one resource with the same
    * UriPattern already registered.
    *
    * @param resourceFactory root resource
    * @throws ResourcePublicationException if resource can't be published
    *         because resource with the sane {@link UriPattern} already
    *         registered
    */
   public void addResource(final ObjectFactory<AbstractResourceDescriptor> resourceFactory)
   {
      UriPattern pattern = resourceFactory.getObjectModel().getUriPattern();
      synchronized (rootResources)
      {
         for (ObjectFactory<AbstractResourceDescriptor> resource : rootResources)
         {
            if (resource.getObjectModel().getUriPattern().equals(resourceFactory.getObjectModel().getUriPattern()))
            {
               throw new ResourcePublicationException("Resource class "
                  + resourceFactory.getObjectModel().getObjectClass().getName()
                  + " can't be registered. Resource class " + resource.getObjectModel().getObjectClass().getName()
                  + " with the same pattern " + pattern + " already registered.");
            }
         }
         rootResources.add(resourceFactory);
         Collections.sort(rootResources, RESOURCE_COMPARATOR);
         size++;
         if (LOG.isDebugEnabled())
            LOG.debug("Add resource: " + resourceFactory.getObjectModel());
      }
   }

   /**
    * Register root resource.
    *
    * @param resourceClass class of candidate to be root resource
    * @return true if resource was bound and false if resource was not bound
    *         cause it is not root resource
    * @deprecated use {@link #addResource(Class, MultivaluedMap)} instead
    */
   public boolean bind(final Class<?> resourceClass)
   {
      try
      {
         addResource(resourceClass, null);
         return true;
      }
      catch (ResourcePublicationException e)
      {
         LOG.warn(e.getMessage());
         return false;
      }
   }

   /**
    * Register supplied Object as singleton root resource if it has valid JAX-RS
    * annotations and no one resource with the same UriPattern already
    * registered.
    *
    * @param resource candidate to be root resource
    * @return true if resource was bound and false if resource was not bound
    *         cause it is not root resource
    * @deprecated use {@link #addResource(Object, MultivaluedMap)} instead
    */
   public boolean bind(final Object resource)
   {
      try
      {
         addResource(resource, null);
         return true;
      }
      catch (ResourcePublicationException e)
      {
         LOG.warn(e.getMessage());
         return false;
      }
   }

   /**
    * Clear the list of ResourceContainer description.
    */
   public void clear()
   {
      synchronized (rootResources)
      {
         rootResources.clear();
         size = 0;
      }
   }

   /**
    * Get root resource matched to <code>requestPath</code>.
    *
    * @param requestPath request path
    * @param parameterValues see {@link ApplicationContext#getParameterValues()}
    * @return root resource matched to <code>requestPath</code> or
    *         <code>null</code>
    */
   public ObjectFactory<AbstractResourceDescriptor> getMatchedResource(String requestPath, List<String> parameterValues)
   {
      ObjectFactory<AbstractResourceDescriptor> resourceFactory = null;
      synchronized (rootResources)
      {
         for (ObjectFactory<AbstractResourceDescriptor> resource : rootResources)
         {
            if (resource.getObjectModel().getUriPattern().match(requestPath, parameterValues))
            {
               // all times will at least 1
               int len = parameterValues.size();
               // If capturing group contains last element and this element is
               // neither null nor '/' then ResourceClass must contains at least one
               // sub-resource method or sub-resource locator.
               if (parameterValues.get(len - 1) != null && !parameterValues.get(len - 1).equals("/"))
               {
                  int subresnum =
                     resource.getObjectModel().getSubResourceMethods().size()
                        + resource.getObjectModel().getSubResourceLocators().size();
                  if (subresnum == 0)
                     continue;
               }
               resourceFactory = resource;
               break;
            }
         }
      }
      return resourceFactory;
   }

   /**
    * @return all registered root resources
    */
   public List<ObjectFactory<AbstractResourceDescriptor>> getResources()
   {
      return rootResources;
   }

   /**
    * @return all registered root resources
    */
   @Deprecated
   public List<AbstractResourceDescriptor> getRootResources()
   {
      List<AbstractResourceDescriptor> l = new ArrayList<AbstractResourceDescriptor>(size);
      synchronized (rootResources)
      {
         for (ObjectFactory<AbstractResourceDescriptor> f : rootResources)
         {
            l.add(f.getObjectModel());
         }
      }
      return l;
   }

   /**
    * @return number of bound resources
    */
   public int getSize()
   {
      return size;
   }

   /**
    * Remove root resource of supplied class from root resource collection.
    *
    * @param clazz root resource class
    * @return removed resource or <code>null</code> if resource of specified
    *         class not found
    */
   @SuppressWarnings("unchecked")
   public ObjectFactory<AbstractResourceDescriptor> removeResource(Class clazz)
   {
      ObjectFactory<AbstractResourceDescriptor> resource = null;
      synchronized (rootResources)
      {
         for (Iterator<ObjectFactory<AbstractResourceDescriptor>> iter = rootResources.iterator(); iter.hasNext()
            && resource == null;)
         {
            ObjectFactory<AbstractResourceDescriptor> next = iter.next();
            Class<?> resourceClass = next.getObjectModel().getObjectClass();
            if (clazz.equals(resourceClass))
            {
               iter.remove();
               resource = next;
            }
         }
         if (resource != null)
         {
            size--;
            if (LOG.isDebugEnabled())
               LOG.debug("Remove resource: " + resource.getObjectModel());
         }
      }
      return resource;
   }

   /**
    * Remove root resource with specified UriTemplate from root resource
    * collection.
    *
    * @param path root resource path
    * @return removed resource or <code>null</code> if resource for specified
    *         template not found
    */
   public ObjectFactory<AbstractResourceDescriptor> removeResource(String path)
   {
      ObjectFactory<AbstractResourceDescriptor> resource = null;
      UriPattern pattern = new UriPattern(path);
      synchronized (rootResources)
      {
         for (Iterator<ObjectFactory<AbstractResourceDescriptor>> iter = rootResources.iterator(); iter.hasNext()
            && resource == null;)
         {
            ObjectFactory<AbstractResourceDescriptor> next = iter.next();
            UriPattern resourcePattern = next.getObjectModel().getUriPattern();
            if (pattern.equals(resourcePattern))
            {
               iter.remove();
               resource = next;
            }
         }
         if (resource != null)
         {
            size--;
            if (LOG.isDebugEnabled())
               LOG.debug("Remove resource: " + resource.getObjectModel());
         }
      }
      return resource;
   }

   /**
    * Remove root resource of supplied class from root resource collection.
    *
    * @param clazz root resource class
    * @return true if resource was unbound false otherwise
    * @deprecated use {@link #removeResource(Class)}
    */
   @SuppressWarnings("unchecked")
   public boolean unbind(Class clazz)
   {
      return null != removeResource(clazz);
   }

   /**
    * Remove root resource with specified UriTemplate from root resource
    * collection.
    *
    * @param path root resource path
    * @return true if resource was unbound false otherwise
    * @deprecated use {@link #removeResource(String)} instead
    */
   public boolean unbind(String path)
   {
      return null != removeResource(path);
   }

}
