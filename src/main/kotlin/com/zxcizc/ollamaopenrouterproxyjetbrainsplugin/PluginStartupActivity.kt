package com.zxcizc.ollamaopenrouterproxyjetbrainsplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

class PluginStartupActivity : ProjectActivity {

    companion object {
        private val proxyServer = ProxyServer()
    }

    override suspend fun execute(project: Project) {
        // 앱이 시작될 때 한 번만 서버를 시작하도록 보장
        if (ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isHeadlessEnvironment) {
            return
        }

        proxyServer.start()

        // IDE 종료 시 서버를 중지하기 위한 훅
        val parentDisposable = Disposable {}
        Disposer.register(ApplicationManager.getApplication(), parentDisposable)
        Disposer.register(parentDisposable, Disposable {
            proxyServer.stop()
        })
    }
}