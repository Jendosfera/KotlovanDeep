if (hasStack) {
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            val next = if (depth >= maxDepth) 0 else depth + 1
            nav.navigate("card/$row/$col?depth=$next") {
                popUpTo("card/$row/$col?depth=$depth") { inclusive = true }
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Blue),
        border = BorderStroke(7.dp, Blue),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Icon(Icons.Default.Layers, null)
        Spacer(Modifier.size(6.dp))
        Text("Вложенная ячейка ($depth/$maxDepth)")
    }
}