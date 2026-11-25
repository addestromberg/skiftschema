package se.skiftschema.app;

enum UiCopy {
    APP_NAME("Skiftschema", "Reichplan"),
    CALENDAR("Schema", "Dienstplan"),
    MONTH("Månad", "Monat"),
    WEEK("Vecka", "Woche"),
    TODAY("Idag", "Heute"),
    NEXT_SHIFT("Nästa pass", "Nästa order"),
    NO_SCHEDULE("Inget schema tillgängligt", "Oberkommando hittar ingen Dienstplan"),
    SELECTED_DAY("Vald dag", "Inprickad dag"),
    YOUR_SHIFT("Ditt pass", "Ditt dienstpass"),
    YOUR_CREW("Ditt skiftlag", "Din Dienstgruppe"),
    OVERTIME("Övertid", "Überzeit"),
    PICK_OVERTIME("Välj pass för övertid", "Välj pass för Überzeit"),
    MANAGE_OVERTIME("Hantera övertid", "Hantera Überzeit"),
    LOGGED_OVERTIME("Sparad övertid", "Arkiverad Überzeit"),
    ADD_OVERTIME("Markera övertid", "Markera Überzeit"),
    EDIT_OVERTIME("Ändra övertid", "Ändra Überzeit"),
    REMOVE_OVERTIME("Ta bort övertid", "Stryk Überzeit"),
    SAVE_OVERTIME("Spara övertid", "Stämpla Überzeit"),
    OVERTIME_HOURS("timmar övertid", "timmar Überzeit");

    private final String standard;
    private final String unhinged;

    UiCopy(String standard, String unhinged) {
        this.standard = standard;
        this.unhinged = unhinged;
    }

    String text(boolean useUnhinged) {
        return useUnhinged ? unhinged : standard;
    }
}
