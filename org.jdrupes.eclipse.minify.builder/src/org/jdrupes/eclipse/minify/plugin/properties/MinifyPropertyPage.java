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

package org.jdrupes.eclipse.minify.plugin.properties;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PropertyPage;
import org.jdrupes.eclipse.minify.plugin.MinifyBuilder;

public abstract class MinifyPropertyPage extends PropertyPage {

	private static final String PATH_TITLE = "Path:";
	private static final String MINIFIER_TITLE = "&Minifier:";

	private Combo selection;

	protected abstract String[][] options();
	
	private void addFirstSection(Composite parent) {
		Composite composite = createDefaultComposite(parent);

		//Label for path field
		Label pathLabel = new Label(composite, SWT.NONE);
		pathLabel.setText(PATH_TITLE);

		// Path text field
		Text pathValueText = new Text(composite, SWT.WRAP | SWT.READ_ONLY);
		pathValueText.setText(((IResource) getElement()).getFullPath().toString());
	}

	private void addSeparator(Composite parent) {
		Label separator = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
		GridData gridData = new GridData();
		gridData.horizontalAlignment = GridData.FILL;
		gridData.grabExcessHorizontalSpace = true;
		separator.setLayoutData(gridData);
	}

	private void addSecondSection(Composite parent) {
		Composite composite = createDefaultComposite(parent);

		// Label for owner field
		Label ownerLabel = new Label(composite, SWT.NONE);
		ownerLabel.setText(MINIFIER_TITLE);

		// Create a single-selection list
	    selection = new Combo(composite, SWT.READ_ONLY);

	    // Add the items, one by one
	    for (int i = 0; i < options().length; i++) {
	    	selection.add(options()[1][i]);
	    }
		selection.setText(options()[1][0]);

		try {
			String minifier = ((IResource) getElement()).getPersistentProperty(
					new QualifiedName(MinifyBuilder.BUILDER_ID, MinifyBuilder.MINIFIER_PROPERTY));
			if (minifier != null) {
			    for (int i = 0; i < options().length; i++) {
			    	if (minifier.equals(options()[0][i])) {
			    		selection.setText(options()[1][i]);
			    		break;
			    	}
			    }
			}
		} catch (CoreException e) {
			// Leave default
		}
	}

	/**
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL);
		data.grabExcessHorizontalSpace = true;
		composite.setLayoutData(data);

		addFirstSection(composite);
		addSeparator(composite);
		addSecondSection(composite);
		return composite;
	}

	private Composite createDefaultComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		composite.setLayout(layout);

		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		composite.setLayoutData(data);

		return composite;
	}

	protected void performDefaults() {
		super.performDefaults();
		// Populate the combo with the default value
		selection.setText(options()[1][0]);
	}
	
	public boolean performOk() {
		// store the value in the owner text field
		try {
			for (int i = 0; i < options().length; i++) {
				if (selection.getText().equals(options()[1][i])) {
					((IResource) getElement()).setPersistentProperty(
							new QualifiedName(MinifyBuilder.BUILDER_ID, 
									MinifyBuilder.MINIFIER_PROPERTY),
							options()[0][i]);
					((IResource)getElement()).getProject().build(
							IncrementalProjectBuilder.FULL_BUILD, 
							MinifyBuilder.BUILDER_ID, null, null);
					break;
				}
			}
		} catch (CoreException e) {
			return false;
		}
		return true;
	}

}