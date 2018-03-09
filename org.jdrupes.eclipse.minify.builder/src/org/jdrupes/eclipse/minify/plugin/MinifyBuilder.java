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

import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
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
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.mozilla.javascript.EvaluatorException;
import org.osgi.service.prefs.Preferences;

public class MinifyBuilder extends IncrementalProjectBuilder {
	
	public static final String BUILDER_ID = "org.jdrupes.eclipse.minify.plugin.minifyBuilder";
	public static final String DONT_MINIFY = "DONT_MINIFY";
	public static final String MINIFIER = "minifier";
	public static final String YUI_COMPRESSOR = "YuiCompressor";
	public static final String YUI_PRESERVE_SEMICOLONS = "preserveSemicolons";
	public static final String YUI_DISABLE_OPTIMIZATIONS = "disableOptimizations";
	
	public static final String GOOGLE_CLOSURE_COMPILER = "GoogleClosureComiler";
	public static final String GCC_OPTIMIZATION = "optWhitespaceOnly";
	public static final String GCC_OPT_WHITESPACE_ONLY = "optWhitespaceOnly";
	public static final String GCC_OPT_SIMPLE = "optSimple";
	public static final String GCC_OPT_ADVANCED = "optAdvanced";
	public static final String GCC_CREATE_MAP_FILE = "createMapFile";
	public static final String GCC_INCLUDE_SOURCE = "includeSource";
	
	private static final String MARKER_TYPE = "org.jdrupes.eclipse.minify.plugin.minifyProblem";

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
			final List<IResource> toProcess = new ArrayList<>();
			getProject().accept(new IResourceVisitor() {
				@Override
				public boolean visit(IResource resource) throws CoreException {
					toProcess.add(resource);
					return true;
				}
			});
			SubMonitor subMonitor = SubMonitor.convert(monitor, toProcess.size());
			for (IResource resource: toProcess) {
				subMonitor.split(1);
				minifyResource(resource, prefs);
			}
		} catch (CoreException e) {
		}
	}

	protected void incrementalBuild(IResourceDelta change,
			IEclipsePreferences prefs, IProgressMonitor monitor) throws CoreException {
		// the visitor does the work.
		List<IResourceDelta> deltas = new ArrayList<>();
		change.accept(new IResourceDeltaVisitor() {
			@Override
			public boolean visit(IResourceDelta delta) throws CoreException {
				// We're only interested in css and js file changes.
				if (delta.getResource() instanceof IFile) {
					IFile file = (IFile)delta.getResource();
					if ("js".equals(file.getFileExtension())
							|| "css".equals(file.getFileExtension())) {
						deltas.add(delta);
					}
				}
				return true;
			}
		});
		// Handle removals first, they may actually be renames.
		deltas.sort((a, b) -> 
			a.getKind() == IResourceDelta.REMOVED 
					&& b.getKind() != IResourceDelta.REMOVED ? -1 : 0);
		SubMonitor subMonitor = SubMonitor.convert(monitor, deltas.size());
		for (IResourceDelta delta: deltas) {
			subMonitor.split(1);
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				// handle added resource
				minifyResource(resource, prefs);
				break;
			case IResourceDelta.REMOVED:
				// handle removed resource
				Preferences resPrefs = PrefsAccess.preferences(resource);
				if ((delta.getFlags() & IResourceDelta.MOVED_TO) != 0) {
					IPath toPath = delta.getMovedToPath();
					IResource toResource = ResourcesPlugin
							.getWorkspace().getRoot().findMember(toPath);
					PrefsAccess.moveResource(resPrefs, resource, 
							PrefsAccess.preferences(toResource), toResource);
				}
				PrefsAccess.removeResource(resPrefs, resource);
				break;
			case IResourceDelta.CHANGED:
				// handle changed resource
				minifyResource(resource, prefs);
				break;
			}
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
		IFile srcFile = (IFile) resource;
		String minifier = prefs.get(PrefsAccess.preferenceKey(resource, MINIFIER), DONT_MINIFY);
		deleteMarkers(srcFile);
		if (minifier.equals(DONT_MINIFY)) {
			return;
		}
		deleteMarkers(srcFile);
		IPath srcPath = srcFile.getProjectRelativePath();
		IPath destPath = srcPath.removeFileExtension().addFileExtension(
				"min." + resource.getFileExtension());
		IFile destFile = srcFile.getProject().getFile(destPath);
		try {
			MinifyRunner producer = null;
			try (PipedInputStream toIFile = new PipedInputStream();
					OutputStream out = new PipedOutputStream(toIFile)) {
				if (resource.getFileExtension().equals("css")) {
					producer = new YuiCssMinifier(this, srcFile, destFile, out, prefs);
				} else if (resource.getFileExtension().equals("js")) {
					if (minifier.equals(YUI_COMPRESSOR)) {
						try {
							producer = new YuiJsMinifier(this, srcFile, destFile, out, prefs);
						} catch (EvaluatorException e) {
							return;
						}
					} else 	if (minifier.equals(GOOGLE_CLOSURE_COMPILER)) {
						producer = new GccMinifier(this, srcFile, destFile, out, prefs);
					}
				}
				producer.start();
				if (!destFile.exists()) {
					destFile.create(toIFile, IResource.FORCE | IResource.DERIVED, null);
				} else {
					destFile.setDerived(true, null);
					destFile.setContents(toIFile, true, true, null);
				}
				destFile.setCharset(producer.destCharset(), null);
			}
			producer.join();
			for (IFile extraFile: producer.createdExtraFiles()) {
				if (!extraFile.isSynchronized(IResource.DEPTH_ZERO)) {
					extraFile.refreshLocal(IResource.DEPTH_ZERO, null);
				}
				if (extraFile.exists()) {
					extraFile.setDerived(true, null);
				}
			}
			processMarkers();
			producer.checkException();
		} catch (CoreException e) {
			throw e;
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, BUILDER_ID, e.getMessage(), e));
		}
	}

	public MessageConsole minifierConsole() {
		IConsoleManager conMan = ConsolePlugin.getDefault().getConsoleManager();
		IConsole[] existing = conMan.getConsoles();
		for (int i = 0; i < existing.length; i++)
			if ("Minifier".equals(existing[i].getName()))
				return (MessageConsole) existing[i];
		//no console found, so create a new one
		MessageConsole myConsole = new MessageConsole("Minifier", null);
		conMan.addConsoles(new IConsole[] { myConsole });
		return myConsole;
	}
	
	public static abstract class MinifyRunner extends Thread {
		
		private MinifyBuilder builder;
		private Exception exception = null;
		private List<IFile> createdExtraFiles = new ArrayList<>();

		protected MinifyRunner(MinifyBuilder builder) {
			this.builder = builder;
		}

		protected MinifyBuilder builder() {
			return builder;
		}
		
		public List<IFile> createdExtraFiles() {
			return createdExtraFiles;
		}

		protected void addCreatedExtraFile(IFile file) {
			createdExtraFiles.add(file);
		}
		
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
		
		protected abstract String destCharset();
	}
	
	private class MarkerInfo {
		public IFile file;
		public String message;
		public int lineNumber;
		public int severity;
		
		public MarkerInfo(IFile file, String message, int lineNumber, int severity) {
			this.file = file;
			this.message = message;
			this.lineNumber = lineNumber;
			this.severity = severity;
		}
	}
	
	private List<MarkerInfo> pendingMarkers = new ArrayList<>();

	/**
	 * Marker can only be created in the "main" thread, so the information
	 * has to be buffered.
	 * 
	 * @param file
	 * @param message
	 * @param lineNumber
	 * @param severity
	 */
	public void addMarker(IFile file, String message, int lineNumber, int severity) {
		pendingMarkers.add(new MarkerInfo(file, message, lineNumber, severity));
	}

	private void processMarkers() {
		try {
			for (MarkerInfo mi: pendingMarkers) {
				IMarker marker = mi.file.createMarker(MARKER_TYPE);
				marker.setAttribute(IMarker.MESSAGE, mi.message);
				marker.setAttribute(IMarker.SEVERITY, mi.severity);
				if (mi.lineNumber == -1) {
					mi.lineNumber = 1;
				}
				marker.setAttribute(IMarker.LINE_NUMBER, mi.lineNumber);
			}
			pendingMarkers.clear();
		} catch (CoreException e) {
		}
	}

}
