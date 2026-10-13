package com.plataformacitas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
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
}
