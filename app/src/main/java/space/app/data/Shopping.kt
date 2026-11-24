package space.app.data

enum class Section(val label: String) {
    PRODUCE("Fruit & veg"),
    DAIRY("Dairy & eggs"),
    BAKERY("Bakery"),
    MEAT("Meat & fish"),
    PANTRY("Pantry"),
    FROZEN("Frozen"),
    DRINKS("Drinks"),
    HOUSEHOLD("Household"),
    OTHER("Other"),
}

private val sectionWords: Map<String, Section> = buildMap {
    fun put(section: Section, words: String) = words.split(" ").forEach { put(it, section) }
    put(Section.PRODUCE, "apple apples banana bananas orange oranges lemon lemons lime limes mango mangoes grapes " +
        "tomato tomatoes potato potatoes onion onions garlic ginger carrot carrots spinach lettuce cucumber " +
        "pepper peppers capsicum chilli chillies coriander mint broccoli cauliflower cabbage peas beans avocado " +
        "berries strawberries papaya pineapple watermelon fruit veg vegetables mushrooms")
    put(Section.DAIRY, "milk curd yogurt yoghurt cheese paneer butter ghee cream eggs egg")
    put(Section.BAKERY, "bread buns bun rolls croissant croissants cake pav")
    put(Section.MEAT, "chicken mutton fish prawns meat beef pork bacon ham sausages")
    put(Section.PANTRY, "rice atta flour dal lentils sugar salt oil pasta noodles cereal oats spices tea coffee " +
        "honey jam ketchup sauce biscuits chips nuts almonds")
    put(Section.FROZEN, "ice icecream frozen")
    put(Section.DRINKS, "juice water soda cola beer wine")
    put(Section.HOUSEHOLD, "soap shampoo detergent toothpaste tissues toilet paper napkins bags batteries " +
        "bulb cleaner sponge")
}

/** Which aisle an item belongs in, by its first known word. */
fun sectionOf(item: String): Section =
    item.lowercase().split(Regex("""[^\p{L}]+""")).firstNotNullOfOrNull { sectionWords[it] } ?: Section.OTHER

/** "milk, eggs\nbread" → three items. Splitting on "and" is the understanding rules' job (M4). */
fun splitShoppingItems(text: String): List<String> = text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
