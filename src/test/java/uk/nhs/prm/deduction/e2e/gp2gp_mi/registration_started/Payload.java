package uk.nhs.prm.deduction.e2e.gp2gp_mi.registration_started;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.nhs.prm.deduction.e2e.gp2gp_mi.registration_started.Registration;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Payload {

    private Registration registration;
}
