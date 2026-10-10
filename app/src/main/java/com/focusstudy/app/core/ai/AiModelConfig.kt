package com.focusstudy.app.core.ai

object AiModelConfig {
    const val PLANNING_MODEL = "gemini-2.5-flash"
    const val ANALYSIS_MODEL = "gemini-2.5-flash"
    const val FAST_CHAT_MODEL = "gemini-2.5-flash"

    // Prompt schema versions
    const val PROMPT_VERSION_PLANNER = "planner.v1"
    const val PROMPT_VERSION_SYLLABUS = "syllabus-parser.v1"
    const val PROMPT_VERSION_INSIGHTS = "insights.v1"
    const val PROMPT_VERSION_COACH = "coach.v1"

    // Default embedded API key for out-of-the-box Gemini 1.5 Flash features
    var DEFAULT_GEMINI_API_KEY: String = com.focusstudy.app.BuildConfig.DEFAULT_GEMINI_API_KEY

    // Feature toggles
    var isMockAiModeEnabled: Boolean = false
}
