// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: real official Processing example sketch (Convolution/Shiffman)
// FIXED: parseParam only handled empty "[]" brackets -- any bracket
//        containing a size or a second dimension failed outright.
//        Now handles "float m[3][3]", "float m[][3]", "float m[3]",
//        "float m[]" -- all rendering as "float (*m)[3]" (pointer-to-
//        array, the correct C++ decay) when inner dims are present.
//
// This is a common, natural thing to write when passing a fixed-size
// 2D kernel or matrix to a convolution/image-processing function.
color convolution(int x, int y, float matrix[3][3], int sz) {
    float r = 0.0;
    for (int i = 0; i < sz; i++)
        for (int j = 0; j < sz; j++)
            r += matrix[i][j];
    return color(r);
}
