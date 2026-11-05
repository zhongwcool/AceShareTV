package com.alex.aceshare.legal

/**
 * In-app privacy policy. Keep [docs/privacy-policy.html] in sync when this text changes.
 * The Play Console URL must serve the same disclosures.
 */
object PrivacyPolicy {
    const val updatedOn = "2026年11月5日"

    val sections: List<PolicySection> = listOf(
        PolicySection(
            heading = "适用范围",
            paragraphs = listOf(
                "本政策适用于 AceShare（手机和平板）以及 AceShare TV（Android 电视）。它们是局域网客户端，用来连接你自己在同一网络中运行的 AceShare 电脑程序。",
                "本政策说明应用会访问、使用和保存哪些信息。Google Play 商店页面上的开发者名称应与下面的名称一致。",
            ),
        ),
        PolicySection(
            heading = "开发者",
            paragraphs = listOf(
                "名称：zhong Alex",
                "邮箱：zhongwcool@163.com",
            ),
        ),
        PolicySection(
            heading = "应用访问的信息",
            paragraphs = listOf(
                "应用会访问下列信息。这些信息只用于连接你自己的电脑，开发者不会收到它们。",
                "本地网络：局域网里 AceShare 服务的名称、IP 地址或主机名、端口，以及你上次选择的服务器。",
                "你选中的文件：通过系统文件选择器读取你主动选择的文件，并上传到你的电脑。应用不读取整个相册，也不申请访问全部文件的权限。",
                "来自你电脑的内容：文件名、文本、图片，以及你打开的其他文件，用于在本机查看、播放，或按你的操作下载和复制。",
                "剪贴板：只有在你点击复制时，才把对应文本写入剪贴板。",
                "本机连接记录：服务器名称、地址、端口，以及是否自动进入上次的服务器。",
            ),
        ),
        PolicySection(
            heading = "使用目的",
            paragraphs = listOf(
                "这些信息只用于发现你的电脑、连接你选中的服务器，以及完成你发起的浏览、预览、上传、下载、复制和删除。",
                "当前版本不把这些信息用于广告或统计分析。",
            ),
        ),
        PolicySection(
            heading = "收集与分享",
            paragraphs = listOf(
                "当前版本不把上述信息传给开发者，也不与第三方分享。应用没有统计组件、没有广告组件，也不提供账号。",
                "从 Google Play 安装的版本不会为了检查更新去访问开发者的服务器。",
                "从 GitHub 安装、并且带有应用内更新的版本，只在检查更新时访问 https://api.github.com 。该请求只包含发布版本信息，不包含你的文件、文本、剪贴板内容或电脑地址。",
                "我们不出售个人数据。",
            ),
        ),
        PolicySection(
            heading = "传输",
            paragraphs = listOf(
                "应用与你的电脑之间使用局域网明文 HTTP，例如 http://电脑地址:端口，传输过程不加密。请只在你信任的网络中使用。开发者无法看到这些流量。",
            ),
        ),
        PolicySection(
            heading = "保存位置",
            paragraphs = listOf(
                "连接记录保存在本机。系统自动备份可能把这份记录存入你自己的 Google 账号，开发者无法读取这份备份。",
                "你确认下载的文件由系统下载管理器保存到你的设备。",
                "已经上传的文件保存在你的电脑上。",
            ),
        ),
        PolicySection(
            heading = "系统权限",
            paragraphs = listOf(
                "互联网和网络状态：用于连接你的电脑。",
                "无线组播：用于在局域网中发现 AceShare。",
                "本地网络（Android 17 及更高版本）：用于发现并连接局域网设备。系统弹出权限请求之前，应用会先说明这项权限的用途。",
                "存储（仅 Android 9 及更早版本）：用于把下载保存到设备。",
            ),
        ),
        PolicySection(
            heading = "保留与删除",
            paragraphs = listOf(
                "本应用没有账号，所以没有单独的账号删除功能。卸载应用，或在系统设置中清除本应用数据，都会删除本机上的连接记录。",
                "已经传到电脑上的文件需要在电脑上的 AceShare 中删除。",
            ),
        ),
        PolicySection(
            heading = "儿童",
            paragraphs = listOf(
                "本应用不是儿童应用，不以 13 岁以下儿童为目标用户。",
            ),
        ),
        PolicySection(
            heading = "广告与应用内购买",
            paragraphs = listOf(
                "当前版本没有广告，也没有应用内购买，不读取广告标识符。",
                "如果以后的版本要加入广告或应用内购买，会先更新本政策，说明新增的数据类型、接收这些数据的对象，以及你可以如何拒绝或管理，然后再发布该版本。",
            ),
        ),
        PolicySection(
            heading = "本政策的更新",
            paragraphs = listOf(
                "更新时会修改文首的日期。应用内的政策文本会与公网页面一起更换。",
            ),
        ),
        PolicySection(
            heading = "联系",
            paragraphs = listOf(
                "隐私问题请发送邮件到 zhongwcool@163.com。",
            ),
        ),
    )
}

data class PolicySection(
    val heading: String,
    val paragraphs: List<String>,
)
