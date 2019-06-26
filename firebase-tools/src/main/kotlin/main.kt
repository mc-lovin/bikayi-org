import com.squareup.kotlinpoet.*
import org.json.JSONObject
import java.lang.Exception

val jsonString = """
    {
       "animals": {
            "@key": "a",
            "@type": "int",
            "age": "a",
            "height": "b",
            "weight": "w"
       },
       "birds": "b",
       "dogs": "d"
    }
"""

object KEYS {
    const val KEY = "@key"
    const val TYPE = "@type"
    val ALL_KEYS = listOf(KEY, TYPE)
}

data class Node(
    val name: String,
    val key: String,
    val children: List<Node> = mutableListOf(),
    val type: String? = null
)

class SchemaGenerator(val schema: String) {
    val errors = mutableListOf<String>()

    fun addClass(node: Node, objectBuilder: TypeSpec.Builder) {
        node.children.forEach {
            objectBuilder.addProperty(
                PropertySpec.builder(it.name, String::class)
                    .initializer("%S", it.key)
                    .build()
            )
            if (it.children.isNotEmpty()) {
                val childObjectBuilder = TypeSpec.objectBuilder(it.name)
                addClass(it, childObjectBuilder)
                objectBuilder.addType(childObjectBuilder.build())
            }
        }
    }

    fun generate(): String {
        errors.clear()
        val node = generate(schema, "", "") ?: return ""
        val builder = TypeSpec.classBuilder("KEYS")
        addClass(node, builder)
        val fileBuilder = FileSpec.builder("", "KEYS")
            .addType(builder.build())
        return fileBuilder.build().toString()
    }

    fun safeJson(json: String): JSONObject? {
        return try {
            JSONObject(json)
        } catch (exception: Exception) {
            null
        }
    }

    fun safeString(json: JSONObject, key: String): String? {
        return try {
            json.getString(key)
        } catch (exception: Exception) {
            null
        }
    }

    fun generate(schema: String, name: String, absolutePath: String): Node? {
        val json = JSONObject(schema)
        json.keys().forEach { it.toString() }
        val keys: List<String> = json.keys().asSequence().map { it.toString() }.toList()
        val childKeys: List<String> = keys.filter {
            KEYS.ALL_KEYS.indexOf(it) == -1
        }

        var key: String? = ""
        if (absolutePath.isNotEmpty()) {
            if (childKeys.indexOf(KEYS.KEY) == -1) {
                key = safeString(json, KEYS.KEY)

            }
            if (key.isNullOrEmpty()) {
                key = name
            }
        }

        val children = childKeys.map {
            val childString = safeString(json, it) ?: return@map null
            safeJson(childString) ?: return@map Node(it, childString)
            generate(childString, it, "$absolutePath/it")
        }.filterNotNull()

        return Node(name, key!!, children)
    }
}

fun main() {
    val generator = SchemaGenerator(jsonString)
    val nodes = generator.generate()
    println(nodes)
    println(generator.errors)
}