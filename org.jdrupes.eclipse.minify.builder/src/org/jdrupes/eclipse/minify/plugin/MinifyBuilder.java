/*
 * Eclipse Minify Builder
 * Copyright (C) 2017  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.jdrupes.eclipse.minify.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class MinifyBuilder extends IncrementalProjectBuilder {
	
	public static final String BUILDER_ID = "org.jdrupes.eclipse.minify.plugin.minifyBuilder";
	public static final String DONT_MINIFY = "DONT_MINIFY";
	public static final String MINIFIER = "minifier";
	public static final String YUI_COMPRESSOR = "YuiCompressor";
	public static final String YUI_PRESERVE_SEMICOLONS = "preserveSemicolons";
	public static final String YUI_DISABLE_OPTIMIZATIONS = "disableOptimizations";
	
	private static final String MARKER_TYPE = "org.jdrupes.eclipse.minify.plugin.minifyProblem";

	/**
	 * Returns the builder preferences for the given resource.
	 * 
	 * @param resource the resource
	 * @return the preferences
	 */
	public static Preferences preferences(IResource resource) {
		ProjectScope projectScope = new ProjectScope(resource.getProject());
		return projectScope.getNode(BUILDER_ID);
	}

	/**
	 * Generate the key for a given resource and its associated property.
	 * 
	 * @param resource the resource
	 * @param property the property
	 * @return the key
	 */
	public static String preferenceKey(IResource resource, String property) {
		return property + "//" + resource.getProjectRelativePath().toPortableString();
	}

	/**
	 * Remove a resource (i.e. all its properties) from the builder's preferences.
	 * 
	 * @param prefs the preferences
	 * @param resource the resource
	 * @throws BackingStoreException
	 */
	public static void removeResource(Preferences prefs, IResource resource) 
			throws BackingStoreException {
		String[] keys = prefs.keys();
		for (String key: keys) {
    		if (key.endsWith("/" + resource.getProjectRelativePath().toPortableString())) {
    			prefs.remove(key);
    		}
    	}
		prefs.flush();
	}

	/**
	 * Associate one resource's properties with another resource.
	 * 
	 * @param fromPrefs the preferences to take the properties from
	 * @param fromResource the resource to take the properties from
	 * @param toPrefs the preferences to move the properties to
	 * @param toResource the resource to associated with the properties
	 * @throws BackingStoreException
	 */
	public static void moveResource(Preferences fromPrefs, IResource fromResource,
			Preferences toPrefs, IResource toResource) 
			throws BackingStoreException {
    	String[] keys = fromPrefs.keys();
    	for (String key: keys) {
    		if (key.endsWith("/" + fromResource.getProjectRelativePath().toPortableString())) {
    			String resourcePreference = key.substring(0, key.indexOf('/'));
    			toPrefs.put(preferenceKey(toResource, resourcePreference), fromPrefs.get(key, ""));
    			fromPrefs.remove(key);
    		}
    	}
    	fromPrefs.flush();
    	toPrefs.flush();
	}
	
	@Override
	protected IProject[] build(int kind, @SuppressWarnings("rawtypes") Map args,
			IProgressMonitor monitor) throws CoreException {
		ProjectScope projectScope = new ProjectScope(getProject());
		IEclipsePreferences prefs = projectScope.getNode(BUILDER_ID);
		if (kind == FULL_BUILD) {
			fullBuild(prefs, monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(prefs, monitor);
			} else {
				incrementalBuild(delta, prefs, monitor);
			}
		}
		return null;
	}

	protected void fullBuild(final IEclipsePreferences prefs, final IProgressMonitor monitor)
			throws CoreException {
		try {
			getProject().accept(new MinifyResourceVisitor(prefs));
		} catch (CoreException e) {
		}
	}

	class MinifyResourceVisitor implements IResourceVisitor {
		IEclipsePreferences prefs;

		public MinifyResourceVisitor(IEclipsePreferences prefs) {
			this.prefs = prefs;
		}
		
		public boolean visit(IResource resource) throws CoreException {
			minifyResource(resource, prefs);
			return true;
		}
	}
	
	protected void incrementalBuild(IResourceDelta delta,
			IEclipsePreferences prefs, IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		delta.accept(new SampleDeltaVisitor(prefs));
	}
	
	class SampleDeltaVisitor implements IResourceDeltaVisitor {
		IEclipsePreferences prefs;
		
		public SampleDeltaVisitor(IEclipsePreferences prefs) {
			this.prefs = prefs;
		}
		
		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				minifyResource(resource, prefs);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				minifyResource(resource, prefs);
				break;
			}
			//return true to continue visiting children.
			return true;
		}
	}

	protected void clean(IProgressMonitor monitor) throws CoreException {
		// delete markers set and files created
		getProject().deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
	}

	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	/**
	 * Does the actual work.
	 * 
	 * @param resource the resource to minify.
	 * @param prefs the preferences store with the resource's minify properties
	 * @throws CoreException
	 */
	private void minifyResource(IResource resource, IEclipsePreferences prefs) 
			throws CoreException {
		if (!(resource instanceof IFile)) {
			return;
		}
		String minifier = prefs.get(preferenceKey(resource, MINIFIER), DONT_MINIFY);
		if (minifier.equals(DONT_MINIFY)) {
			return;
		}
		IFile srcFile = (IFile) resource;
		deleteMarkers(srcFile);
		IPath srcPath = srcFile.getProjectRelativePath();
		IPath destPath = srcPath.removeFileExtension().addFileExtension(
				"min." + resource.getFileExtension());
		IFile destFile = srcFile.getProject().getFile(destPath);
		String destCharset = destFile.exists() ? destFile.getCharset() : srcFile.getCharset();
		try {
			SafeRunner producer = null;
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(srcFile.getContents(), srcFile.getCharset()));
					PipedInputStream toIFile = new PipedInputStream();
					Writer out = new OutputStreamWriter(new PipedOutputStream(toIFile), destCharset)) {
				if (resource.getFileExtension().equals("css")) {
					producer = new CssMinifier(in, out);
				} else if (resource.getFileExtension().equals("js")) {
					try {
						producer = new JsMinifier(in, out, srcFile, prefs,
								new MinifyErrorHandler(srcFile));
					} catch (EvaluatorException e) {
						return;
					}
				}
				producer.start();
				if (!destFile.exists()) {
					destFile.create(toIFile, IResource.FORCE | IResource.DERIVED, null);
					destFile.setCharset(destCharset, null);
				} else {
					destFile.setDerived(true, null);
					destFile.setContents(toIFile, true, true, null);
				}
			}
			producer.join();
			producer.checkException();
		} catch (CoreException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, BUILDER_ID, e.getMessage(), e));
		}
	}

	private class SafeRunner extends Thread {
		
		private Exception exception = null;

		public void checkException() throws Exception {
			if (exception != null) {
				throw exception;
			}
		}

		@Override
		public void run() {
			try {
				runSafe();
			} catch (Exception e) {
				exception = e;
			}
		}

		protected void runSafe() throws Exception {
		}
	}
	
	private class CssMinifier extends SafeRunner {
		private Reader in;
		private Writer out;

		public CssMinifier(Reader in, Writer out) {
			this.in = in;
			this.out = out;
		}

		@Override
		protected void runSafe() throws Exception {
			try {
				new CssCompressor(in).compress(out, -1);
			} finally {
				out.close();
			}
		}
	}
	
	private class JsMinifier extends SafeRunner {
		private Writer out;
		private JavaScriptCompressor compressor;
		boolean preserveSemicolons;
		boolean disableOptimizations;
		
		public JsMinifier(Reader in, Writer out, 
				IResource resource, IEclipsePreferences prefs, ErrorReporter reporter)
			throws IOException {
			this.out = out;
			preserveSemicolons = prefs.getBoolean(
					preferenceKey(resource, YUI_PRESERVE_SEMICOLONS), true);
			disableOptimizations = prefs.getBoolean(
					preferenceKey(resource, YUI_DISABLE_OPTIMIZATIONS), true);
			compressor = new JavaScriptCompressor(in, reporter);
		}

		@Override
		protected void runSafe() throws Exception {
			try {
				compressor.compress(out, 512, false, false, 
						preserveSemicolons, disableOptimizations);
			} finally {
				out.close();
			}
		}
	}
	
	/**
	 * The error reporter for the YUICompressor.
	 */
	class MinifyErrorHandler implements ErrorReporter {
		
		private IFile file;

		public MinifyErrorHandler(IFile file) {
			this.file = file;
		}

		@Override
		public void error(String message, String sourceName, int line, 
				String lineSource, int lineOffset) {
			MinifyBuilder.this.addMarker(file, message, line, IMarker.SEVERITY_ERROR);
		}

		@Override
		public void warning(String message, String sourceName, int line, 
				String lineSource, int lineOffset) {
			MinifyBuilder.this.addMarker(file, message, line, IMarker.SEVERITY_WARNING);
		}

		@Override
		public EvaluatorException runtimeError(String message, String sourceName, int line, 
				String lineSource, int lineOffset) {
			return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
		}
	}

	private void addMarker(IFile file, String message, int lineNumber,
			int severity) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			if (lineNumber == -1) {
				lineNumber = 1;
			}
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		} catch (CoreException e) {
		}
	}

}
