package com.metrolist.innertube.models.response

import com.metrolist.innertube.models.Thumbnails
import kotlinx.serialization.Serializable

/**
 * Vendored addition: every channel a Google account can act as.
 *
 * A Google account is not one YouTube identity but several — the personal
 * channel, and any brand channels it owns — and the official apps let the
 * listener pick between them. The library that ships here only ever asked for
 * the active one, so a session opened on the wrong identity showed the wrong
 * library with no way to change it.
 *
 * The shape is the account menu's, not a page's: `account/accounts_list`
 * answers with an action that opens a popup, and the accounts are sections
 * inside it. Read off a real response — the first attempt at this assumed a
 * `contents` at the root and failed before it could say so.
 *
 * Every field is optional. This is read for what it happens to carry, and a
 * branch that is missing on some accounts must not fail the whole parse.
 */
@Serializable
data class AccountsListResponse(
    val actions: List<Action>? = null,
) {
    @Serializable
    data class Action(
        val openPopupAction: OpenPopupAction? = null,
    )

    @Serializable
    data class OpenPopupAction(
        val popup: Popup? = null,
    )

    @Serializable
    data class Popup(
        val multiPageMenuRenderer: MultiPageMenuRenderer? = null,
    )

    @Serializable
    data class MultiPageMenuRenderer(
        val sections: List<Section>? = null,
    )

    @Serializable
    data class Section(
        val accountSectionListRenderer: AccountSectionListRenderer? = null,
    )

    @Serializable
    data class AccountSectionListRenderer(
        val contents: List<SectionContent>? = null,
    )

    @Serializable
    data class SectionContent(
        val accountItemSectionRenderer: AccountItemSectionRenderer? = null,
    )

    @Serializable
    data class AccountItemSectionRenderer(
        val contents: List<ItemContent>? = null,
    )

    @Serializable
    data class ItemContent(
        val accountItem: AccountItem? = null,
    )

    /**
     * A piece of text, written either way.
     *
     * YouTube says the same thing in two shapes — `simpleText` for a plain
     * string, `runs` for one built out of segments — and this endpoint uses the
     * first while most of the module uses the second. Declaring these as `Runs`
     * is what made every account fail to decode, quietly, leaving a list that
     * looked empty when the response held three.
     */
    @Serializable
    data class Label(
        val simpleText: String? = null,
        val runs: List<Run>? = null,
    ) {
        val text: String? get() = simpleText ?: runs?.firstOrNull()?.text
    }

    @Serializable
    data class Run(val text: String? = null)

    @Serializable
    data class AccountItem(
        val accountName: Label? = null,
        val accountByline: Label? = null,
        val accountPhoto: Thumbnails? = null,
        val isSelected: Boolean = false,
        val serviceEndpoint: ServiceEndpoint? = null,
    )

    @Serializable
    data class ServiceEndpoint(
        val selectActiveIdentityEndpoint: SelectActiveIdentityEndpoint? = null,
    )

    @Serializable
    data class SelectActiveIdentityEndpoint(
        val supportedTokens: List<SupportedToken>? = null,
    )

    @Serializable
    data class SupportedToken(
        val pageIdToken: PageIdToken? = null,
        val datasyncIdToken: DatasyncIdToken? = null,
    )

    @Serializable
    data class PageIdToken(val pageId: String? = null)

    @Serializable
    data class DatasyncIdToken(val datasyncIdToken: String? = null)
}
