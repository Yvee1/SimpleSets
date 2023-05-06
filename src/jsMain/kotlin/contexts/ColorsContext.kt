package contexts

import ColorRGB
import react.createContext

interface Colors {
    val defaultColors: List<ColorRGB>
    var colors: List<ColorRGB>
}

val ColorsContext = createContext<Colors>()