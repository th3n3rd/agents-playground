package com.example

import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.format.Moshi

interface Recipes {
    fun findAllBy(name: String): List<Recipe>
}

data class Recipe(val name: String, val ingredients: List<Pair<String, String>>)

class MealApiRecipes(val outgoing: HttpHandler) : Recipes {
    override fun findAllBy(name: String): List<Recipe> {
        val response = outgoing(
            Request.Companion(Method.GET, "https://www.themealdb.com/api/json/v1/1/search.php").query("s", name))
        return Search.Response.json(response)
            .meals
            .map {
                Recipe(
                    name = it.strMeal,
                    ingredients = listOf(
                        it.strMeasure1,
                        it.strMeasure2,
                        it.strMeasure3,
                        it.strMeasure4,
                        it.strMeasure5,
                        it.strMeasure6,
                        it.strMeasure7,
                        it.strMeasure8,
                        it.strMeasure9,
                        it.strMeasure10,
                        it.strMeasure11,
                        it.strMeasure12,
                        it.strMeasure13,
                        it.strMeasure14,
                        it.strMeasure15,
                        it.strMeasure16,
                        it.strMeasure17,
                        it.strMeasure18,
                        it.strMeasure19,
                        it.strMeasure20,
                    ).zip(listOf(
                        it.strIngredient1,
                        it.strIngredient2,
                        it.strIngredient3,
                        it.strIngredient4,
                        it.strIngredient5,
                        it.strIngredient6,
                        it.strIngredient7,
                        it.strIngredient8,
                        it.strIngredient9,
                        it.strIngredient10,
                        it.strIngredient11,
                        it.strIngredient12,
                        it.strIngredient13,
                        it.strIngredient14,
                        it.strIngredient15,
                        it.strIngredient16,
                        it.strIngredient17,
                        it.strIngredient18,
                        it.strIngredient19,
                        it.strIngredient20,
                    ))
                )
            }
    }

    object Search {
        data class Response(val meals: List<Meal>) {
            companion object {
                val json = Moshi.autoBody<Response>().toLens()
            }
        }

        data class Meal(
            val strMeal: String,
            val strMeasure1: String = "",
            val strMeasure2: String = "",
            val strMeasure3: String = "",
            val strMeasure4: String = "",
            val strMeasure5: String = "",
            val strMeasure6: String = "",
            val strMeasure7: String = "",
            val strMeasure8: String = "",
            val strMeasure9: String = "",
            val strMeasure10: String = "",
            val strMeasure11: String = "",
            val strMeasure12: String = "",
            val strMeasure13: String = "",
            val strMeasure14: String = "",
            val strMeasure15: String = "",
            val strMeasure16: String = "",
            val strMeasure17: String = "",
            val strMeasure18: String = "",
            val strMeasure19: String = "",
            val strMeasure20: String = "",
            val strIngredient1: String = "",
            val strIngredient2: String = "",
            val strIngredient3: String = "",
            val strIngredient4: String = "",
            val strIngredient5: String = "",
            val strIngredient6: String = "",
            val strIngredient7: String = "",
            val strIngredient8: String = "",
            val strIngredient9: String = "",
            val strIngredient10: String = "",
            val strIngredient11: String = "",
            val strIngredient12: String = "",
            val strIngredient13: String = "",
            val strIngredient14: String = "",
            val strIngredient15: String = "",
            val strIngredient16: String = "",
            val strIngredient17: String = "",
            val strIngredient18: String = "",
            val strIngredient19: String = "",
            val strIngredient20: String = "",
        )
    }
}