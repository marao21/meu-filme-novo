package com.example.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Baixa o APK da atualizacao e abre o instalador.
 *
 * Um WebView nao baixa nada sozinho: ele mostra paginas, e um link de arquivo
 * simplesmente nao faz nada. Era por isso que, dentro do aplicativo, apertar
 * o botao de atualizar nao dava sinal de vida nenhum — nem erro.
 *
 * Quem baixa aqui e o DownloadManager do proprio Android: ele mostra o
 * progresso na barra de avisos, sobrevive a tela apagada e continua se a
 * pessoa sair do aplicativo.
 */
object BaixarAtualizacao {

    private const val NOME = "meu-filme-novo.apk"

    fun ehApk(url: String, mimeType: String?): Boolean {
        val semParametros = url.substringBefore('?').substringBefore('#').lowercase()
        if (semParametros.endsWith(".apk")) return true
        return mimeType?.contains("vnd.android.package-archive", ignoreCase = true) == true
    }

    fun baixar(context: Context, url: String, userAgent: String?) {
        val gerenciador = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (gerenciador == null) {
            abrirNoNavegador(context, url)
            return
        }

        // Uma copia nova a cada vez: deixar a antiga faria o instalador abrir
        // a versao de ontem sem avisar.
        val pasta = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        File(pasta, NOME).takeIf { it.exists() }?.delete()

        val pedido = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Atualizacao do aplicativo")
            setDescription("Baixando a versao nova")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, NOME)
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
        }

        val id = try {
            gerenciador.enqueue(pedido)
        } catch (e: Throwable) {
            abrirNoNavegador(context, url)
            return
        }

        Toast.makeText(context, "Baixando a atualizacao...", Toast.LENGTH_SHORT).show()
        esperarOFim(context, gerenciador, id)
    }

    private fun esperarOFim(context: Context, gerenciador: DownloadManager, id: Long) {
        val aplicacao = context.applicationContext
        val ouvinte = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val qual = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (qual != id) return
                try {
                    aplicacao.unregisterReceiver(this)
                } catch (e: Throwable) {
                }
                abrirInstalador(aplicacao, id)
            }
        }

        val filtro = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            aplicacao.registerReceiver(ouvinte, filtro, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            aplicacao.registerReceiver(ouvinte, filtro)
        }
    }

    private fun abrirInstalador(context: Context, id: Long) {
        val arquivo = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), NOME)
        if (!arquivo.exists()) {
            Toast.makeText(context, "A atualizacao nao chegou inteira.", Toast.LENGTH_LONG).show()
            return
        }

        // O instalador e outro aplicativo, e desde o Android 7 nao se entrega
        // um caminho de arquivo direto a ele: vai um endereco temporario,
        // valido so para este arquivo e so para quem recebe.
        val endereco = try {
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", arquivo)
        } catch (e: Throwable) {
            Toast.makeText(context, "Nao deu para abrir o instalador.", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(endereco, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(
                context,
                "Permita a instalacao de aplicativos desta origem nos ajustes.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun abrirNoNavegador(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Throwable) {
            Toast.makeText(context, "Nao deu para baixar a atualizacao.", Toast.LENGTH_LONG).show()
        }
    }
}
