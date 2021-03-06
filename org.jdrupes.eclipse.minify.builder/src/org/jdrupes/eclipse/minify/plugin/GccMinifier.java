package org.jdrupes.eclipse.minify.plugin;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.ui.console.MessageConsole;
import org.jdrupes.eclipse.minify.plugin.MinifyBuilder.MinifyRunner;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.SourceMap;

public class GccMinifier extends MinifyRunner {

	private IFile srcFile;
	private IFile mapFile;
	private OutputStream out;
	private String outCharset;
	private MessageConsole console;
	private CompilationLevel compilationLevel;
	private boolean createMapFile;
	private boolean includeSource;
	
	public GccMinifier(MinifyBuilder builder, IFile srcFile, IFile destFile, 
			OutputStream out, IEclipsePreferences prefs)
		throws IOException, CoreException {
		super(builder);
		this.srcFile = srcFile;
		this.out = out;
		this.outCharset = destFile.exists() ? destFile.getCharset() : "ascii";
		console = builder.minifierConsole();

		String optLevel = prefs.get(PrefsAccess.preferenceKey(
				srcFile, MinifyBuilder.GCC_OPTIMIZATION),
				MinifyBuilder.GCC_OPT_WHITESPACE_ONLY);
		switch (optLevel) {
		case MinifyBuilder.GCC_OPT_ADVANCED:
			compilationLevel = CompilationLevel.ADVANCED_OPTIMIZATIONS;
			break;
		case MinifyBuilder.GCC_OPT_SIMPLE:
			compilationLevel = CompilationLevel.SIMPLE_OPTIMIZATIONS;
			break;
		default:
			compilationLevel = CompilationLevel.WHITESPACE_ONLY;
		}
		createMapFile = prefs.getBoolean(PrefsAccess.preferenceKey(
				srcFile, MinifyBuilder.GCC_CREATE_MAP_FILE), false);
		if (createMapFile) {
			IPath destPath = destFile.getProjectRelativePath();
			IPath mapPath = destPath.addFileExtension("map");
			mapFile = destFile.getProject().getFile(mapPath);
			addCreatedExtraFile(mapFile);
		}
		includeSource = prefs.getBoolean(PrefsAccess.preferenceKey(
				srcFile, MinifyBuilder.GCC_INCLUDE_SOURCE), false);
	}

	@Override
	public String destCharset() {
		return outCharset;
	}

	@Override
	protected void runSafe() throws Exception {
		PrintStream stdout = new PrintStream(out);
		try {
			CommandLineRunner clr = new GccCommandLineRunner(
					new BufferedInputStream(srcFile.getContents()),
					stdout, 
					new PrintStream(console.newMessageStream()));
			clr.setExitCodeReceiver((r) -> { return null; }); 
			clr.run();
		} finally {
			stdout.close();
		}
	}
	
	private class GccCommandLineRunner extends CommandLineRunner {
		private GccCommandLineRunner(InputStream in, PrintStream out, PrintStream err)
			throws CoreException {
			super(new String[0], in, out, err);
			getCommandLineConfig().setCharset(srcFile.getCharset());
			if (mapFile != null) {
				getCommandLineConfig().setCreateSourceMap(mapFile.getLocation().toOSString());
				getCommandLineConfig().setSourceMapLocationMappings(Arrays.asList(
						new SourceMap.LocationMapping("stdin", srcFile.getName())));
				getCommandLineConfig().setOutputWrapper(
						"%output%\n//# sourceMappingURL=" + mapFile.getName());
			}
		}

		@Override
		protected CompilerOptions createOptions() {
			 CompilerOptions options = super.createOptions();
			 compilationLevel.setOptionsForCompilationLevel(options);
			 options.setOutputCharset(Charset.forName(outCharset));
			 options.setErrorHandler(new GccErrorHandler(srcFile));
			 options.setSourceMapIncludeSourcesContent(includeSource);
			 return options;
		}
	}

	/**
	 * The error reporter for the GCC.
	 */
	private class GccErrorHandler implements ErrorHandler {
		
		private IFile file;

		public GccErrorHandler(IFile file) {
			this.file = file;
		}

		@Override
		public void report(CheckLevel level, JSError error) {
			int imLevel;
			switch (level) {
			case ERROR:
				imLevel = IMarker.SEVERITY_ERROR;
				break;
			case WARNING:
				imLevel = IMarker.SEVERITY_WARNING;
				break;
			default:
				return;
			}
			builder().addMarker(file, error.description, error.getLineNumber(), imLevel);
		}
	}
}