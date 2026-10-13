package com.plataformacitas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.plataformacitas.shared.tenancy.BusinessScopedEntity;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    static final ArchRule DOMAIN_IS_PURE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..application..", "..adapter..")
            .allowEmptyShould(true);

    static final ArchRule APPLICATION_WITHOUT_ADAPTERS = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .allowEmptyShould(true);

    static final ArchRule VERTICAL_NAMES_STAY_IN_VERTICAL = noClasses()
            .that().resideOutsideOfPackage("com.plataformacitas.vertical..")
            .should().haveSimpleNameContaining("Peluqueria");

    private static final DescribedPredicate<JavaClass> HAVE_BUSINESS_ID_FIELD =
            new DescribedPredicate<>("have a businessId field") {
                @Override
                public boolean test(JavaClass c) {
                    return c.getAllFields().stream().anyMatch(f -> f.getName().equals("businessId"));
                }
            };

    static final ArchRule ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED = classes()
            .that().areAnnotatedWith(Entity.class).and(HAVE_BUSINESS_ID_FIELD)
            .should().beAssignableTo(BusinessScopedEntity.class);

    static final ArchRule GLOBAL_ENTITIES_ONLY_IN_IDENTITY_OR_PLATFORM = classes()
            .that().areAnnotatedWith(Entity.class).and().areNotAssignableTo(BusinessScopedEntity.class)
            .should().resideInAnyPackage("com.plataformacitas.identity..", "com.plataformacitas.platform..");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.plataformacitas");

    @Test
    void domainIsPure() {
        DOMAIN_IS_PURE.check(PRODUCTION);
    }

    @Test
    void applicationDoesNotDependOnAdapters() {
        APPLICATION_WITHOUT_ADAPTERS.check(PRODUCTION);
    }

    @Test
    void verticalNamesStayInVertical() {
        VERTICAL_NAMES_STAY_IN_VERTICAL.check(PRODUCTION);
    }

    @Test
    void domainRuleCatchesPersistenceInDomain() {
        JavaClasses fixture = new ClassFileImporter().importPackages("archfixtures.baddomain");
        assertThrows(AssertionError.class, () -> DOMAIN_IS_PURE.check(fixture));
    }

    @Test
    void entitiesWithBusinessIdAreScoped() {
        ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED.check(PRODUCTION);
    }

    @Test
    void globalEntitiesOnlyInIdentityOrPlatform() {
        GLOBAL_ENTITIES_ONLY_IN_IDENTITY_OR_PLATFORM.check(PRODUCTION);
    }

    @Test
    void entityRuleCatchesUnscopedBusinessId() {
        JavaClasses fixture = new ClassFileImporter().importPackages("archfixtures.rogue");
        assertThrows(AssertionError.class, () -> ENTITIES_WITH_BUSINESS_ID_ARE_SCOPED.check(fixture));
    }
}
