package org.jdrupes.eclipse.minify.plugin;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.ui.console.MessageConsole;
import org.jdrupes.eclipse.minify.plugin.MinifyBuilder.MinifyRunner;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CommandLineRunner;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.ErrorHandler;
import com.google.javascript.jscomp.JSError;

public class GccMinifier extends MinifyRunner {

	private IFile srcFile;
	private OutputStream out;
	private String outCharset;
	private MessageConsole console;
	private CompilationLevel compilationLevel;
	
	public GccMinifier(MinifyBuilder builder, IFile srcFile, IFile destFile, 
			OutputStream out, IEclipsePreferences prefs)
		throws IOException, CoreException {
		super(builder);
		this.srcFile = srcFile;
		this.out = out;
		this.outCharset = destFile.exists() ? destFile.getCharset() : "ascii";
		console = builder.minifierConsole();

		String optLevel = prefs.get(Startup.preferenceKey(
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
	}

	@Override
	public String destCharset() {
		return outCharset;
	}

	@Override
	protected void runSafe() throws Exception {
		try {
			CommandLineRunner clr = new GccCommandLineRunner(
					new BufferedInputStream(srcFile.getContents()),
					new PrintStream(out), 
					new PrintStream(console.newMessageStream()));
			clr.setExitCodeReceiver((r) -> { return null; }); 
			clr.run();
		} finally {
			out.close();
		}
	}
	
	private class GccCommandLineRunner extends CommandLineRunner {
		private GccCommandLineRunner(InputStream in, PrintStream out, PrintStream err)
			throws CoreException {
			super(new String[0], in, out, err);
			getCommandLineConfig().setCharset(srcFile.getCharset());
		}

		@Override
		protected CompilerOptions createOptions() {
			 CompilerOptions options = super.createOptions();
			 compilationLevel.setOptionsForCompilationLevel(options);
			 options.setOutputCharset(Charset.forName(outCharset));
			 options.setErrorHandler(new GccErrorHandler(srcFile));
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