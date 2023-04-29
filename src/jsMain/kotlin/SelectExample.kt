import web.cssom.Display
import emotion.react.css
//import mui.base.OptionUnstyled
//import mui.base.OptionUnstyledProps
//import mui.base.SelectUnstyled
//import mui.base.SelectUnstyledProps
//import mui.material.MenuItem
//import mui.material.Select
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.option
import react.useState

external interface SelectExampleProps: Props {
    var onLoadExample: (ExampleInput) -> Unit
}

val SelectExample = FC<SelectExampleProps> { props ->
    var selectedExampleInput: ExampleInput by useState(ExampleInput.NYC)
    div {
        label {
            title = "Select example input"
            div {
                +"Example input"
            }
            ReactHTML.select {
                css {
                    display = Display.block
                }
                value = selectedExampleInput.name
                ExampleInput.values().forEach {
                    option {
                        value = it.name
                        +it.name
                    }
                }
                onChange = {
                    selectedExampleInput = ExampleInput.valueOf(it.target.value)
                }
            }
        }
        button {
            css {
                display = Display.block
            }
            +"Load example"
            onClick = {
                props.onLoadExample(selectedExampleInput)
            }
        }
    }
}

//val MuiSelectExample = FC<SelectExampleProps> { props ->
//    var selectedExampleInput: ExampleInput by useState(ExampleInput.NYC)
//    div {
//        label {
//            title = "Select example input"
//            div {
//                +"Example input"
//            }
////            val SelectExampleInput: FC<SelectUnstyledProps<String>> = SelectUnstyled
////            val ExampleInputOption: FC<OptionUnstyledProps<String>> = OptionUnstyled
//            Select {
////                css {
////                    display = Display.block
////                }
//                value = selectedExampleInput.name
//                ExampleInput.values().forEach {
//                    MenuItem {
//                        value = it.name
//                        +it.name
//                    }
//                }
//                onChange = { _, _ ->
////                    selectedExampleInput = ExampleInput.valueOf(it!!)
//                }
//            }
//        }
//        button {
//            css {
//                display = Display.block
//            }
//            +"Load example"
//            onClick = {
//                props.onLoadExample(selectedExampleInput)
//            }
//        }
//    }
//}
