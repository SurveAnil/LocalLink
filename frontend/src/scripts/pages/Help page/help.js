const searchInput = document.getElementById("help-search");
const topicCards = Array.from(
    document.querySelectorAll(".topic-card")
);
const noResults = document.getElementById("no-results");

function setAccordionState(card, expanded) {
    const button = card.querySelector(".topic-toggle");
    const panel = document.getElementById(
        button.getAttribute("aria-controls")
    );

    button.setAttribute("aria-expanded", String(expanded));
    panel.hidden = !expanded;
    card.classList.toggle("is-open", expanded);
}

// Keep at most one topic expanded at a time.
topicCards.forEach((card) => {
    const button = card.querySelector(".topic-toggle");

    button.addEventListener("click", () => {
        const expanded =
            button.getAttribute("aria-expanded") === "true";

        if (!expanded) {
            topicCards.forEach((otherCard) => {
                if (otherCard !== card) {
                    setAccordionState(otherCard, false);
                }
            });
        }

        setAccordionState(card, !expanded);
    });
});

// Search topic titles, descriptions, and associated keywords.
searchInput.addEventListener("input", () => {
    const query = searchInput.value.trim().toLowerCase();
    let visibleCount = 0;

    topicCards.forEach((card) => {
        const searchableText = [
            card.textContent,
            card.dataset.search || ""
        ].join(" ").toLowerCase();

        const matches = searchableText.includes(query);

        card.hidden = !matches;

        if (matches) {
            visibleCount++;
        }
    });

    noResults.hidden = visibleCount !== 0;
});

// Initialize all accordion sections in the collapsed state.
topicCards.forEach((card) => {
    setAccordionState(card, false);
});
