package org.jdrupes.eclipse.minify.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

public class YuiJsMinifier extends YuiMinifier {
	private JavaScriptCompressor compressor;
	private boolean preserveSemicolons;
	private boolean disableOptimizations;
	private Writer writer;
	private String outCharset;
	
	public YuiJsMinifier(MinifyBuilder builder, IFile srcFile, IFile destFile, 
			OutputStream out, IEclipsePreferences prefs)
		throws IOException, CoreException {
		super (builder);
		preserveSemicolons = prefs.getBoolean(
				Startup.preferenceKey(srcFile, MinifyBuilder.YUI_PRESERVE_SEMICOLONS), true);
		disableOptimizations = prefs.getBoolean(
				Startup.preferenceKey(srcFile, MinifyBuilder.YUI_DISABLE_OPTIMIZATIONS), true);
		outCharset = destFile.exists() ? destFile.getCharset() : srcFile.getCharset();
		writer = new OutputStreamWriter(out, outCharset);
		compressor = new JavaScriptCompressor(new BufferedReader(
				new InputStreamReader(srcFile.getContents(), srcFile.getCharset())), 
				new YuiMinifier.MinifyErrorHandler(srcFile));
	}

	@Override
	public String destCharset() {
		return outCharset;
	}
	
	@Override
	protected void runSafe() throws Exception {
		try {
			compressor.compress(writer, 512, false, false, 
					preserveSemicolons, disableOptimizations);
		} finally {
			writer.close();
		}
	}
}