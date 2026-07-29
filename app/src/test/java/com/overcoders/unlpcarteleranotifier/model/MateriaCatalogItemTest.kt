/** Verifica la validación compartida de identificadores del catálogo de materias. */
package com.overcoders.unlpcarteleranotifier.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MateriaCatalogItemTest {
    @Test
    fun acceptsAndNormalizesPositiveNumericIdentifiers() {
        assertEquals(42, " 042 ".toMateriaCatalogIdOrNull())
    }

    @Test
    fun rejectsIdentifiersThatCannotRepresentRemoteSubjects() {
        listOf("", "materia", "0", "-7").forEach { identifier ->
            assertNull(identifier.toMateriaCatalogIdOrNull())
        }
    }
}
