package org.mycore.frontend.editor.validation;

public class MCRRequiredValidator extends MCRValidatorBase {

    @Override
    public boolean hasRequiredProperties() {
        return hasProperty("required");
    }

    @Override
    protected boolean isValidOrDie(String input) throws Exception {
        boolean required = Boolean.valueOf(getProperty("required"));
        boolean empty = (input == null) || (input.trim().isEmpty());
        return !(required && empty);
    }
}
