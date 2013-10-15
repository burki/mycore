package org.mycore.frontend.xeditor.validation;

import org.jaxen.JaxenException;
import org.jdom2.JDOMException;
import org.mycore.frontend.xeditor.MCRBinding;

public abstract class MCRValidationRule {

    private String xPath;

    public MCRValidationRule(String baseXPath, String relativeXPath) {
        this.xPath = relativeXPath != null ? relativeXPath : baseXPath;
    }

    public boolean validate(MCRValidationResults results, MCRBinding root) throws JaxenException, JDOMException {
        MCRBinding binding = new MCRBinding(xPath, false, root);
        boolean isValid = validateBinding(results, binding);
        binding.detach();
        return isValid;
    }

    public abstract boolean validateBinding(MCRValidationResults results, MCRBinding binding);
}