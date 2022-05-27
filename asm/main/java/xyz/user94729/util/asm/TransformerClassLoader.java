/*
 * Copyright (C) 2021 user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.user94729.util.asm;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.util.ArrayUtil;

/**
 * This is a class loader based on a {@link URLClassLoader} that allows class data and bytecode to be edited while the class is loaded using
 * {@link #addClassTransformer(ClassTransformer)}. Classes may also be generated at run time using {@link #addClassGenerator(ClassGenerator)}.<br>
 * <br>
 * A usage example is below:
 * 
 * <pre>
 * <code>
TransformerClassLoader classLoader = new TransformerClassLoader();

// only classes in this package will be loaded by this class loader and be transformable,
// others will be loaded by the URLClassLoader implementation
classLoader.addTarget("com.example.");

classLoader.addClassTransformer((name, data) -> {
	// do changes to class binary here
	return data;
});

Class&lt;?&gt; mainClass = classLoader.loadClass("com.example.Main");
Method mainMethod = mainClass.getMethod("main", String[].class);
mainClass.invoke(null, new Object[] { args });
 * </code>
 * </pre>
 */
public class TransformerClassLoader extends URLClassLoader {

	private static final Logger logger = Logger.create();

	private final List<String> targetClasses = new ArrayList<>();
	private boolean whitelistMode = true;

	private final List<ClassTransformer> transformers = new ArrayList<>();
	private final List<ClassGenerator> generators = new ArrayList<>();

	/**
	 * Creates a new {@link TransformerClassLoader} with <b>urls</b> set to the {@link URL}s returned by {@link #getClassPathURLs()} and <b>parent</b> set to
	 * <code>null</code>.
	 * 
	 * @see #TransformerClassLoader(URL[], ClassLoader)
	 */
	public TransformerClassLoader() {
		this(getClassPathURLs());
	}

	/**
	 * Creates a new {@link TransformerClassLoader} with <b>parent</b> set to <code>null</code>.
	 * 
	 * @param urls URLs to load classes from
	 * @see #TransformerClassLoader(URL[], ClassLoader)
	 */
	public TransformerClassLoader(URL[] urls) {
		this(urls, null);
	}

	/**
	 * Creates a new {@link TransformerClassLoader} with <b>urls</b> set to the {@link URL}s returned by {@link #getClassPathURLs()}.
	 * 
	 * @param parent The parent class loader
	 * @see #TransformerClassLoader(URL[], ClassLoader)
	 */
	public TransformerClassLoader(ClassLoader parent) {
		this(getClassPathURLs(), parent);
	}

	/**
	 * Creates a new {@link TransformerClassLoader} with the given <b>urls</b> and <b>parent</b> class loader.<br>
	 * <br>
	 * Care need to be taken when setting the <b>parent</b> class loader, because it is called first by {@link ClassLoader#loadClass(String)}. If it finds the requested class
	 * already (which, for example, is the case when using the system class loader with the same classpath), this class loader does not have the opportunity to intercept class
	 * loading and call the class transformers. The default and recommended value for <b>parent</b> is <code>null</code>.
	 * 
	 * @param urls   URLs to load classes from
	 * @param parent The parent class loader
	 */
	public TransformerClassLoader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}


	/**
	 * Adds a package to the list of packages that should or should not be loaded by this class loader. Only classes in packages loaded by this class loader are transformable.
	 * Classes not in this list are loaded by the <code>URLClassLoader</code> implementation.<br>
	 * <br>
	 * Whether this class loader should or should not load packages in this list is determined by the whitelistMode setting set using {@link #setWhitelistMode(boolean)}, which
	 * is <code>true</code> by default, meaning only packages in this list are loaded by this class loader and transformable.
	 * 
	 * @param pkgName The package name prefix
	 */
	public void addTarget(String pkgName) {
		this.targetClasses.add(pkgName);
	}

	/**
	 * 
	 * @param whitelistMode Whether classes in the list of packages set using {@link #addTarget(String)} are classes that should be loaded by this class loader
	 */
	public void setWhitelistMode(boolean whitelistMode) {
		this.whitelistMode = whitelistMode;
	}

	/**
	 * Adds a new {@link ClassTransformer} that is called every time an existing class is loaded by this class loader.
	 * 
	 * @param transformer The transformer
	 */
	public void addClassTransformer(ClassTransformer transformer) {
		this.transformers.add(transformer);
	}

	/**
	 * Adds a new {@link ClassGenerator} that is called every time an attempt is made to load a class that does not exist but in the list of classes that should be loaded by
	 * this class loader.
	 * 
	 * @param generator The generator
	 */
	public void addClassGenerator(ClassGenerator generator) {
		this.generators.add(generator);
	}


	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		if(this.whitelistMode != classInList(this.targetClasses, name))
			return super.findClass(name);

		int pei = name.lastIndexOf('.');
		String packageName = pei > 0 ? name.substring(0, pei) : null;

		CodeSource cs = null;
		byte[] classData = null;

		String path = name.replace('.', '/') + ".class";
		URL pathUrl = super.findResource(path);
		if(pathUrl != null){
			logger.trace("Found class file for ", name, " at ", pathUrl);
			try{
				classData = ArrayUtil.readInputStreamToByteArray(pathUrl.openStream());

				classData = this.transformClass(name, classData);
				if(classData == null)
					throw new ClassNotFoundException(name);

				URLConnection conn = pathUrl.openConnection();
				if(conn instanceof JarURLConnection){
					JarURLConnection jconn = (JarURLConnection) conn;
					JarFile jarFile = jconn.getJarFile();
					JarEntry entry = jconn.getJarEntry();
					cs = new CodeSource(pathUrl, entry.getCodeSigners());
					if(packageName != null && super.getPackage(packageName) == null){
						logger.trace("Defining package ", packageName, " with JAR file manifest");
						super.definePackage(packageName, jarFile.getManifest(), jconn.getJarFileURL());
					}
				}
			}catch(IOException e){
				throw new ClassNotFoundException(name, e);
			}
		}else{
			logger.trace("No class file found for ", name, ", attempting generation with ", this.generators.size(), " generators");
			classData = this.generateClass(name);
		}

		if(classData != null){
			if(packageName != null && super.getPackage(packageName) == null){
				logger.trace("Defining package ", packageName);
				super.definePackage(packageName, null, null, null, null, null, null, null);
			}
			logger.trace("Defining class ", name, " (", classData.length, " bytes, source: ", cs, ")");
			return super.defineClass(name, classData, 0, classData.length, cs);
		}else
			throw new ClassNotFoundException(name);
	}


	private byte[] transformClass(String name, byte[] data) {
		for(ClassTransformer transformer : this.transformers){
			data = transformer.transform(name, data);
		}
		return data;
	}

	private byte[] generateClass(String name) {
		for(ClassGenerator generator : this.generators){
			byte[] data = generator.generate(name);
			if(data != null)
				return data;
		}
		return null;
	}


	private static boolean classInList(List<String> list, String name) {
		for(String cn : list){
			if(name.startsWith(cn)){
				return true;
			}
		}
		return false;
	}

	/**
	 * If the system class loader is a {@link URLClassLoader}, the {@link URL}s of that class loader ({@link URLClassLoader#getURLs()}) are returned.<br>
	 * Otherwise, the list of <code>URL</code>s is inferred from the <code>java.class.path</code> system property.
	 * 
	 * @return The list of configured classpath URLs
	 */
	protected static URL[] getClassPathURLs() {
		ClassLoader sysloader = ClassLoader.getSystemClassLoader();
		if(sysloader instanceof URLClassLoader){
			return ((URLClassLoader) sysloader).getURLs();
		}else{
			logger.debug("System class loader is not a URLClassLoader, falling back to java.class.path to get classpath URLs");
			String[] paths = System.getProperty("java.class.path").split(System.getProperty("path.separator"));
			URL[] urls = new URL[paths.length];
			for(int i = 0; i < paths.length; i++){
				try{
					urls[i] = new File(paths[i]).toURI().toURL();
				}catch(MalformedURLException e){
					throw new RuntimeException("Collecting classpath URLs failed", e);
				}
			}
			return urls;
		}
	}
}
