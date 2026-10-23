package io.github.denisgames54.botw

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * PLACEHOLDER (fase 1). Na fase 2 vira `class GameActivity : SDLActivity()` carregando libmain.so
 * (runtime + cemu_gpu + guest) e recebendo BOTW_GAME / BOTW_USER_ROOT como variaveis de ambiente.
 */
class GameActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = intent.getStringExtra("BOTW_GAME")
        val user = intent.getStringExtra("BOTW_USER_ROOT")
        setContentView(TextView(this).apply {
            setPadding(48, 48, 48, 48)
            text = "Motor nativo ainda nao ligado (fase 2).\n\nBOTW_GAME=$game\nBOTW_USER_ROOT=$user"
        })
    }
}
