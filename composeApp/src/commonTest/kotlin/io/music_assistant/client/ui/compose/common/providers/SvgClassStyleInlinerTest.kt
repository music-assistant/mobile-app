package io.music_assistant.client.ui.compose.common.providers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SvgClassStyleInlinerTest {
    @Test
    fun `svg without a style block is returned unchanged`() {
        val svg = """<svg><path fill="#fff" class="a"/></svg>"""
        assertSame(svg, inlineSvgClassStyles(svg))
    }

    @Test
    fun `class rules inside CDATA become a style attribute`() {
        val svg = """<svg><style type="text/css"><![CDATA[	.st0{fill:#E5A00D;}	.st1{fill:#282A2D;}]]></style>""" +
            """<path class="st1" d="M0"/><polygon class="st0" points="1"/></svg>"""
        val result = inlineSvgClassStyles(svg)
        assertContains(result, """<path style="fill:#282A2D" class="st1" d="M0"/>""")
        assertContains(result, """<polygon style="fill:#E5A00D" class="st0" points="1"/>""")
    }

    @Test
    fun `rules apply in source order and the inline style stays last`() {
        val svg = """<svg><style>.b { fill: red } .a, .b { stroke: blue; } /* .a{fill:green} */</style>""" +
            """<path class="a  b" style="opacity:0.5"/></svg>"""
        assertContains(
            inlineSvgClassStyles(svg),
            """<path class="a  b" style="fill: red;stroke: blue;opacity:0.5"/>""",
        )
    }

    @Test
    fun `a style block and a comment over several lines are read`() {
        val svg = "<svg><style>\n.a{fill:red}\n/* .b{\nfill:green} */\n</style><path class=\"a\"/><path class=\"b\"/></svg>"
        val result = inlineSvgClassStyles(svg)
        assertContains(result, """<path style="fill:red" class="a"/>""")
        assertContains(result, """<path class="b"/>""")
    }

    @Test
    fun `at rules and non class selectors are skipped`() {
        val svg = """<svg><style>path{stroke:#0628b9}@media (prefers-color-scheme:dark){.a{stroke:#f7a5d8}}""" +
            """.a.b{fill:red} g .a{fill:red} #x{fill:red}</style><path class="a b" id="x"/></svg>"""
        assertEquals(svg, inlineSvgClassStyles(svg))
    }

    @Test
    fun `element without a matching class is unchanged`() {
        val svg = """<svg><style>.a{fill:red}</style><path class="c"/><g/></svg>"""
        assertEquals(svg, inlineSvgClassStyles(svg))
    }

    @Test
    fun `double quotes in a declaration become single quotes`() {
        val svg = """<svg><style>.t{font-family:"Inter"}</style><text class="t"/></svg>"""
        assertContains(inlineSvgClassStyles(svg), """<text style="font-family:'Inter'" class="t"/>""")
    }

    @Test
    fun `base64 svg data uri becomes an inlined Svg model`() {
        val svg = """<svg><style>.a{fill:red}</style><path class="a"/></svg>"""
        val dataUri = "data:image/svg+xml;base64," + kotlin.io.encoding.Base64.encode(svg.encodeToByteArray())
        val model = ProviderIconModel.fromSvg(dataUri) as ProviderIconModel.Svg
        assertContains(model.svgBytes.decodeToString(), """<path style="fill:red" class="a"/>""")
    }

    @Test
    fun `base64 png data uri stays a Png model`() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val dataUri = "data:image/png;base64," + kotlin.io.encoding.Base64.encode(bytes)
        assertEquals(ProviderIconModel.Png(bytes), ProviderIconModel.fromSvg(dataUri))
    }

    private fun assertContains(actual: String, expected: String) =
        kotlin.test.assertContains(actual, expected, message = actual)
}
