package com.sharedlibs.kilogateway;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class FreeModelPolicyTest {
    @Test void knownFreeShape() {
        assertThat(FreeModelPolicy.isFreeModelId("poolside/laguna-s-2.1:free")).isTrue();
        assertThat(FreeModelPolicy.isFreeModelId("kilo-auto/free")).isTrue();
        assertThat(FreeModelPolicy.isFreeModelId("anthropic/claude-sonnet-4.5")).isFalse();
    }
}
