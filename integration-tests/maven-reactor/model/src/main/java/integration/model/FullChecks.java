package integration.model;

import jakarta.validation.GroupSequence;

@GroupSequence({Basic.class, Strict.class})
public interface FullChecks {
}
