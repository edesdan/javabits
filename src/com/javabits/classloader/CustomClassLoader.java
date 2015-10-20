package com.javabits.classloader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <tt>CustomClassLoader</tt> class uses a delegation model to search for
 * classes and resources. Each instance of <tt>CustomClassLoader</tt> has an
 * associated parent class loader. When requested to find a class or resource, a
 * <tt>CustomClassLoader</tt> instance searches for the class or resource itself
 * before delegating the search for the class or resource to its parent class
 * loader. The "app class loader" may serve as the parent of a
 * <tt>CustomClassLoader</tt> instance.
 * 
 * <p>
 * Note that a list of class names to skips for searches in the
 * <tt>CustomClassLoader</tt> classpath can be defined using the
 * {@link #addClassForParentDelegation(String)} method.
 * 
 * @author danieleds
 *
 */
public class CustomClassLoader extends ClassLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(CustomClassLoader.class);

	private final ChildClassLoader childClassLoader;

	private final List<String> delegatedToParent;

	/**
	 * 
	 * Construct a new instance of a <tt>CustomClassLoader</tt> given a list of
	 * URLs.
	 * 
	 * @param classpath
	 *            a list of files (jars) or directories to search for class
	 *            files. Any URL that ends with a '/' is assumed to refer to a
	 *            directory. Otherwise, the URL is assumed to refer to a JAR
	 *            file which will be opened as needed.
	 */
	public CustomClassLoader(List<URL> classpath) {

		super(Thread.currentThread().getContextClassLoader());

		if (classpath.isEmpty()) {
			throw new IllegalArgumentException("The classpath list cannot be empty.");
		}

		URL[] urls = classpath.toArray(new URL[classpath.size()]);

		childClassLoader = new ChildClassLoader(urls, this.getParent());

		delegatedToParent = new ArrayList<String>();

		Thread.currentThread().setContextClassLoader(this);

	}

	/**
	 * Loads the class with the specified <a href="#name">binary name</a>. The
	 * implementation of this method searches for classes in the following
	 * order:
	 *
	 * <ol>
	 *
	 * <li>
	 * <p>
	 * If the class name is contained in the list of classes whose loading is
	 * delegated to the parent class loader, invoke the
	 * {@link #loadClass(String) <tt>loadClass</tt>} method on the parent class
	 * loader.
	 *
	 * <li>
	 * <p>
	 * Invoke {@link #findLoadedClass(String)} to check if the class has already
	 * been loaded by this <tt>CustomClassLoader</tt>.
	 * </p>
	 * </li>
	 *
	 * <li>
	 * <p>
	 * Invoke the {@link #findClass(String)} method to find the class on the
	 * <tt>CustomClassLoader</tt> classpath.
	 * </p>
	 * </li>
	 *
	 *
	 * <li>
	 * <p>
	 * Invoke the {@link #loadClass(String) <tt>loadClass</tt>} method to find
	 * the class on the parent class loader classpath.
	 * </p>
	 * </li>
	 *
	 * </ol>
	 *
	 * <p>
	 * If the class was found using the above steps, and the <tt>resolve</tt>
	 * flag is true, this method will then invoke the
	 * {@link #resolveClass(Class)} method on the resulting <tt>Class</tt>
	 * object.
	 *
	 *
	 * 
	 * @param name
	 *            The <a href="#name">binary name</a> of the class
	 *
	 * @param resolve
	 *            If <tt>true</tt> then resolve the class
	 *
	 * @return The resulting <tt>Class</tt> object
	 *
	 * @throws ClassNotFoundException
	 *             If the class could not be found neither in the
	 *             <tt>CustomClassLoader</tt> classpath nor in the parent class
	 *             loader classpath.
	 */
	@Override
	protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

		LOGGER.debug("loading: {}", name);

		try {
			return childClassLoader.findClass(name);
		} catch (ClassNotFoundException e) {
			// Load the class from the AppClassLoader
			return super.loadClass(name, resolve);
		}

	}

	/**
	 * Add a class name to the list of class names whose loading will be skipped
	 * by this <tt>CustomClassLoader</tt> and delegated to the parent class
	 * loader.
	 * 
	 * @param className
	 *            a string representing the class name to skip.
	 */
	public void addClassForParentDelegation(String className) {
		Objects.requireNonNull(className, "className cannot be null");
		delegatedToParent.add(className);
	}

	/**
	 * Get the list of classes that are delegated to the parent classloader.
	 * 
	 * @return the list of classes for which the search has been delegated
	 *         directly to the parent classloader.
	 */
	public final List<String> getDelegatedClasses() {
		return delegatedToParent;
	}

	private class ChildClassLoader extends URLClassLoader {

		private final ClassLoader parentClassLoader;

		public ChildClassLoader(URL[] urls, ClassLoader parentClassLoader) {
			super(urls, null); // Set the parent to the Bootstrap class loader
			this.parentClassLoader = parentClassLoader; // store the reference
														// to the
														// applicationClassLoader

		}

		@Override
		public Class<?> findClass(String name) throws ClassNotFoundException {

			try {

				if (delegatedToParent.contains(name)) {
					LOGGER.debug("[ findClass(): {} FILTERED >> delegate to parent ]", name);
					return parentClassLoader.loadClass(name);
				}

				// Check if the class was already loaded
				Class<?> loaded = super.findLoadedClass(name);

				if (loaded != null) {
					LOGGER.debug("[ findClass() : {} already loaded by this CustomClassLoader ]", name);
					return loaded;
				}

				// Check if the class is on this URI classLoader path or in the
				// BootStrap classpath
				Class<?> founded = super.findClass(name);
				LOGGER.debug("[ findClass(): {}  found on this CustomClassLoader classpath ]", name);
				return founded;

			} catch (ClassNotFoundException e) {
				// Load the class from the Application class loader path
				LOGGER.debug("[ findClass(): {}  NOT FOUND >> delegate to parent ]", name);
				return parentClassLoader.loadClass(name);
			}

		}

	}

}
