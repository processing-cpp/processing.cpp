// EXPECT: PASS
// CATEGORY: regression-guard
// FOUND: deliberate probing, zero corpus evidence but genuinely common
// FIXED: operator name parsing only consumed one token after "operator"
//        keyword. "operator[]" needs "[" then "]" consumed and folded
//        into the name. Also fixed: "operator()" (call operator).
struct Vec {
    float data[4];
    float& operator[](int i) { return data[i]; }
    const float& operator[](int i) const { return data[i]; }
};
