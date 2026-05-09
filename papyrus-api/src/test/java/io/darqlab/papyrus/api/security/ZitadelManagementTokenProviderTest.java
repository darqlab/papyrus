package io.darqlab.papyrus.api.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZitadelManagementTokenProviderTest {

    @Test
    void returnsPat_whenConfigured() {
        var provider = new ZitadelManagementTokenProvider("tok-abc");
        assertThat(provider.getToken()).isEqualTo("tok-abc");
    }

    @Test
    void throwsIllegalState_whenPatIsBlank() {
        var provider = new ZitadelManagementTokenProvider("");
        assertThatThrownBy(provider::getToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ZITADEL_MGMT_PAT");
    }

    @Test
    void throwsIllegalState_whenPatIsWhitespaceOnly() {
        var provider = new ZitadelManagementTokenProvider("   ");
        assertThatThrownBy(provider::getToken)
                .isInstanceOf(IllegalStateException.class);
    }
}
