enum class GameState { MENU, PLAYING, PAUSED, GAME_OVER };

class StateMachine {
public:
    GameState current = GameState::MENU;
    int score = 0;

    void update() {
        switch (current) {
            case GameState::MENU:
                if (_mousePressed) current = GameState::PLAYING;
                break;
            case GameState::PLAYING:
                score++;
                if (score > 100) current = GameState::GAME_OVER;
                break;
            case GameState::PAUSED:
                break;
            case GameState::GAME_OVER:
                break;
        }
    }

    String stateName() const {
        switch (current) {
            case GameState::MENU: return "Menu";
            case GameState::PLAYING: return "Playing";
            case GameState::PAUSED: return "Paused";
            case GameState::GAME_OVER: return "Game Over";
        }
        return "Unknown";
    }
};

StateMachine* sm = nullptr;

void setup() {
    size(640, 360);
    sm = new StateMachine();
}

void draw() {
    background(0);
    sm->update();
    fill(255);
    text(sm->stateName() + " Score: " + sm->score, 10, 20);
}
