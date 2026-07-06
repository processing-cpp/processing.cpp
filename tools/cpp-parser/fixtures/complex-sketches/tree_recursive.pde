class TreeNode {
public:
    float angle;
    float length;
    std::vector<TreeNode*> children;
    TreeNode* parent = nullptr;

    TreeNode(float a, float l, TreeNode* p = nullptr) : angle(a), length(l), parent(p) {}

    ~TreeNode() {
        for (TreeNode* c : children) {
            delete c;
        }
    }

    void grow(int depth) {
        if (depth <= 0) return;
        TreeNode* left = new TreeNode(angle - 0.3f, length * 0.7f, this);
        TreeNode* right = new TreeNode(angle + 0.3f, length * 0.7f, this);
        children.push_back(left);
        children.push_back(right);
        left->grow(depth - 1);
        right->grow(depth - 1);
    }

    void render(float x, float y) {
        float x2 = x + cos(angle) * length;
        float y2 = y + sin(angle) * length;
        line(x, y, x2, y2);
        for (TreeNode* c : children) {
            c->render(x2, y2);
        }
    }

    int countNodes() const {
        int count = 1;
        for (TreeNode* c : children) {
            count += c->countNodes();
        }
        return count;
    }
};

TreeNode* root = nullptr;

void setup() {
    size(640, 360);
    root = new TreeNode(-PI / 2, 80);
    root->grow(6);
    println("Total nodes: " + str(root->countNodes()));
}

void draw() {
    background(0);
    stroke(255);
    root->render(width / 2, height);
}
