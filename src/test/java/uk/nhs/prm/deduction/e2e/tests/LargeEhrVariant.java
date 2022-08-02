package uk.nhs.prm.deduction.e2e.tests;

public enum LargeEhrVariant {
    SINGLE_LARGE_ATTACHMENT(Patient.WITH_SINGLE_ATTACHMENT_LARGE_EHR, 2),
    MULTIPLE_LARGE_ATTACHMENTS(Patient.WITH_MULTIPLE_ATTACHMENTS_LARGE_EHR, 2),
    LARGE_MEDICAL_HISTORY(Patient.WITH_LARGE_MEDICAL_HISTORY_EHR, 2),
    HIGH_ATTACHMENT_COUNT(Patient.WITH_HIGH_ATTACHMENT_COUNT_LARGE_EHR, 5),
    SUPER_LARGE(Patient.WITH_SUPER_LARGE_EHR, 30);

    private Patient patient;
    private int timeoutMinutes;

    LargeEhrVariant(Patient patient, int timeoutMinutes) {
        this.patient = patient;
        this.timeoutMinutes = timeoutMinutes;
    }

    public Patient patient() {
        return patient;
    }

    public int timeoutMinutes() {
        return timeoutMinutes;
    }
}
