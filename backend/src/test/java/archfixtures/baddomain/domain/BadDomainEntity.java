package archfixtures.baddomain.domain;

import jakarta.persistence.Transient;

public class BadDomainEntity {
    @Transient
    String nombre;
}
