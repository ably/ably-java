ruleset {
    ruleset('rulesets/formatting.xml') {
        // This rule requires a space before the colon, which is unnecessary and less human-readable - especially in our JSON-rich world.
        exclude 'SpaceAroundMapEntryColon'

        // Arbitrary, ending up forcing developers to contort code unecessarily - especially for string definitions.
        exclude 'LineLength'
    }
}
