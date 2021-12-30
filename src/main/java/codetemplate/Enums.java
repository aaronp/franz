package codetemplate;

public class Enums {
     // singleton
    private Enums() {}

    /**
     * Function for creating an Enum instance of the given type for the name
     *
     * @param c1ass the enum class
     * @param value the name of the enum instance
     * @param <A>
     * @return the eun instance
     */
    public static <A> A valueOf(Class<?> c1ass, String value) {
        Enum<?> instance = Enum.valueOf((Class<Enum>) c1ass, value);
        return (A) instance;
    }
}
