ruleset {
    ruleset 'rulesets/basic.xml'

    ruleset('rulesets/formatting.xml') {
        // This rule requires a space before the colon, which is unnecessary and less human-readable - especially in our JSON-rich world.
        exclude 'SpaceAroundMapEntryColon'

        // Arbitrary, ending up forcing developers to contort code unecessarily - especially for string definitions.
        exclude 'LineLength'
    }

    ruleset('rulesets/convention.xml') {
        // Not a valid concern given our context (Gradle build scripts).
        exclude 'CompileStatic'
    }

    ruleset 'rulesets/groovyism.xml'
    ruleset 'rulesets/unnecessary.xml'
    ruleset 'rulesets/unused.xml'
}
