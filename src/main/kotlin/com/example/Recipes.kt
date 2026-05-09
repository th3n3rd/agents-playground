package com.example

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.format.Moshi

interface Recipes {
    fun findAllBy(name: String): List<Recipe>
}

data class Recipe(val name: String)

class MealApiRecipes(val outgoing: HttpHandler) : Recipes {
    override fun findAllBy(name: String): List<Recipe> {
        val response = outgoing(
            Request.Companion(Method.GET, "https://www.themealdb.com/api/json/v1/1/search.php").query("s", name))
        return Search.Response.json(response)
            .meals
            .map { Recipe(name = it.strMeal) }
    }

    object Search {
        data class Response(val meals: List<Meal>) {
            companion object {
                val json = Moshi.autoBody<Response>().toLens()
            }
        }

        data class Meal(val strMeal: String)
    }
}