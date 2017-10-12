package org.jdrupes.eclipse.minify.plugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import com.yahoo.platform.yui.compressor.CssCompressor;

public class YuiCssMinifier extends YuiMinifier {
	private IFile srcFile;
	private OutputStream out;
	private String inCharset;
	private String outCharset;

	public YuiCssMinifier(MinifyBuilder builder, IFile srcFile, 
			IFile destFile, OutputStream out, IEclipsePreferences prefs)
					throws CoreException {
		super(builder);
		this.srcFile = srcFile;
		this.inCharset = srcFile.getCharset();
		this.out = out;
		this.outCharset = destFile.exists() ? destFile.getCharset() : srcFile.getCharset();
	}

	@Override
	public String destCharset() {
		return outCharset;
	}
	
	@Override
	protected void runSafe() throws Exception {
		try (Reader reader = new BufferedReader(new InputStreamReader(
				srcFile.getContents(), inCharset));
				Writer writer = new OutputStreamWriter(out, outCharset)) {
			try {
				new CssCompressor(reader).compress(writer, -1);
			} finally {
				writer.close();
			}
		}
	}
}