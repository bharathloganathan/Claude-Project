package org.example.docsync.model;

import java.util.List;

public record SourceModel(
    List<Route> routes,
    List<ErrorCodeInfo> errorCodes,
    List<Field> studentFields,
    List<String> formParameters,
    List<ServiceMethod> serviceMethods,
    String serviceDigest,
    UiForm uiForm,
    List<FrontendRule> frontendRules,
    List<PayloadEntry> payload,
    List<RuleSpec> backendRules) {

    public SourceModel {
        routes = List.copyOf(routes);
        errorCodes = List.copyOf(errorCodes);
        studentFields = List.copyOf(studentFields);
        formParameters = List.copyOf(formParameters);
        serviceMethods = List.copyOf(serviceMethods);
        frontendRules = List.copyOf(frontendRules);
        payload = List.copyOf(payload);
        backendRules = List.copyOf(backendRules);
    }

    // The model of the E2 documents: the UI parts are absent (uiForm is null) until the UI analysis adds them.
    public SourceModel(
        List<Route> routes,
        List<ErrorCodeInfo> errorCodes,
        List<Field> studentFields,
        List<String> formParameters,
        List<ServiceMethod> serviceMethods,
        String serviceDigest) {
        this(routes, errorCodes, studentFields, formParameters, serviceMethods, serviceDigest, null, List.of(), List.of(), List.of());
    }

    public SourceModel withUi(UiForm form, List<FrontendRule> rules, List<PayloadEntry> entries, List<RuleSpec> backend) {
        return new SourceModel(routes, errorCodes, studentFields, formParameters, serviceMethods, serviceDigest,
            form, rules, entries, backend);
    }

    public record Route(String method, String path, String summary, String requestType, String responseType) {
    }

    public record ErrorCodeInfo(String name, int httpStatus) {
    }

    public record Field(String name, String type) {
    }

    public record ServiceMethod(String name, List<String> parameters, String returnType) {
        public ServiceMethod {
            parameters = List.copyOf(parameters);
        }
    }

    public record UiOption(String value, String label) {
    }

    // maxLength is null when the control has no maxlength attribute; options is empty for a control that is not a select.
    public record UiControl(String id, String name, String tag, String type, boolean required, String maxLength,
                            List<UiOption> options) {
        public UiControl {
            options = List.copyOf(options);
        }
    }

    public record UiForm(List<UiControl> controls, String submitText) {
        public UiForm {
            controls = List.copyOf(controls);
        }
    }

    // minLength and pattern are null when the rule does not set them; pattern is the regex source between the slashes.
    public record FrontendRule(String key, boolean required, Integer minLength, String pattern, String message) {
    }

    // One entry of the payload: apiField is the form parameter name, uiField the FormData key it is read from.
    public record PayloadEntry(String apiField, String uiField) {
    }
}
