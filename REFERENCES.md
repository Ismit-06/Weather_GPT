# 📚 Academic References, Literature Foundations & Copyright Attribution

This document establishes the formal academic citations, literature foundations, and intellectual property attributions for research papers that have informed the architectural design, conversational interfaces, and multimodal grounding methodologies within the **WeatherGPT** project.

---

## ⚖️ Copyright & Fair-Use Disclaimer

> [!NOTE]
> **Copyright Ownership Notice:**  
> The research papers cited and referenced herein remain the exclusive intellectual property and copyright of their respective authors and publishing entities:
> - **Elsevier Inc.** / *Computer Science Review* for Casheekar et al. (2024).
> - **Authors / arXiv** for Ali et al. (2026).
>
> Any archival copies provided within this repository are strictly for **non-commercial academic study, educational reference, and conceptual attribution** under fair-use principles (17 U.S. Code § 107). No commercial claims, warranties, or endorsements by the original authors or publishers are implied. If you are a copyright holder and wish for direct links to replace archived copies, please open an issue in this repository.

---

## 📖 Cited Research Papers

### 1. A Contemporary Review on Chatbots, AI-Powered Virtual Conversational Agents, ChatGPT

* **Full Title:** *A contemporary review on chatbots, AI-powered virtual conversational agents, ChatGPT: Applications, open challenges and future research directions*
* **Authors:** 
  - **Avyay Casheekar** (*Vellore Institute of Technology, Vellore, India*)
  - **Archit Lahiri** (*Vellore Institute of Technology, Vellore, India*)
  - **Kanishk Rath** (*Vellore Institute of Technology, Vellore, India*)
  - **Kaushik Sanjay Prabhakar** (*Vellore Institute of Technology, Vellore, India*)
  - **Kathiravan Srinivasan** (*School of Computer Science and Engineering, Vellore Institute of Technology, Vellore, India*)
* **Journal:** *Computer Science Review*, Volume 52, May 2024, Article 100632
* **Publisher:** Elsevier Inc.
* **DOI:** [10.1016/j.cosrev.2024.100632](https://doi.org/10.1016/j.cosrev.2024.100632)
* **Official Publisher URL:** [ScienceDirect Link](https://www.sciencedirect.com/science/article/pii/S1574013724000169)
* **Archived Reference Copy:** [`references/papers/A_Contemporary_Review_on_Chatbots_AI_Powered_Conversational_Agents_ChatGPT.pdf`](papers/A_Contemporary_Review_on_Chatbots_AI_Powered_Conversational_Agents_ChatGPT.pdf)

#### Abstract Summary & Project Relevance
> Casheekar et al. present a comprehensive, multi-dimensional review examining the architectural evolution of conversational agents, from traditional rule-based chatbots to state-of-the-art transformer and LLM-powered interfaces (such as ChatGPT). The survey dissects natural language processing pipelines, dialog management strategies, user interface/user experience (UI/UX) heuristics, and safety constraints.
>
> **Relevance to WeatherGPT:**  
> This literature informed the design of WeatherGPT's conversational state machine, prompt engineering strategies for domain-constrained meteorological dialog, and the UI/UX paradigms implemented in the interactive **WeatherAI Orb** and chat experience.

#### BibTeX Citation
```bibtex
@article{casheekar2024contemporary,
  title={A contemporary review on chatbots, AI-powered virtual conversational agents, ChatGPT: Applications, open challenges and future research directions},
  author={Casheekar, Avyay and Lahiri, Archit and Rath, Kanishk and Prabhakar, Kaushik Sanjay and Srinivasan, Kathiravan},
  journal={Computer Science Review},
  volume={52},
  pages={100632},
  year={2024},
  publisher={Elsevier},
  doi={10.1016/j.cosrev.2024.100632},
  issn={1574-0137}
}
```

---

### 2. Kissan-Dost: Bridging the Last Mile in Smallholder Precision Agriculture with Conversational IoT

* **Full Title:** *Kissan-Dost: Bridging the Last Mile in Smallholder Precision Agriculture with Conversational IoT*
* **Authors:** 
  - **Muhammad Saad Ali** (*Lahore University of Management Sciences - LUMS, Pakistan*)
  - **Daanish U. Khan** (*LUMS, Pakistan*)
  - **Laiba Intizar Ahmad** (*LUMS, Pakistan*)
  - **Umer Irfan** (*LUMS, Pakistan*)
  - **Maryam Mustafa** (*LUMS, Pakistan*)
  - **Naveed Anwar Bhatti** (*LUMS, Pakistan*)
  - **Muhammad Hamad Alizai** (*LUMS, Pakistan*)
* **Publication Venue / Identifier:** arXiv:2602.08593 [cs.HC, cs.AI, cs.CY]
* **Publication Date:** February 2026
* **Official arXiv URL:** [https://arxiv.org/abs/2602.08593](https://arxiv.org/abs/2602.08593)
* **Direct PDF URL:** [https://arxiv.org/pdf/2602.08593](https://arxiv.org/pdf/2602.08593)
* **Archived Reference Copy:** [`references/papers/Kissan_Dost_Conversational_IoT_Precision_Agriculture.pdf`](papers/Kissan_Dost_Conversational_IoT_Precision_Agriculture.pdf)

#### Abstract Summary & Project Relevance
> Ali et al. introduce *Kissan-Dost*, an innovative multilingual, sensor-grounded conversational system that bridges the "last mile" in rural and agricultural intelligence. The system couples live telemetry (soil, climate, and localized weather measurements) with retrieval-augmented generation (RAG) to deliver actionable, plain-language agricultural and weather guidance via voice and messaging interfaces.
>
> **Relevance to WeatherGPT:**  
> Kissan-Dost's principles of **grounded telemetry fusion**, **traceable meteorological answers**, and **low-latency Indic/vernacular voice delivery** directly inspired WeatherGPT's:
> 1. Multi-source telemetry grounding (integrating live MET Norway observations, IMD hazard data, and CWC reservoir monitoring).
> 2. Multilingual voice synthesis and on-device natural language interfaces for regional users (Hindi, Odia, Telugu, Marathi, Bengali, etc.).
> 3. Zero-hallucination conservative weather advisory rules.

#### BibTeX Citation
```bibtex
@misc{ali2026kissandost,
  title={Kissan-Dost: Bridging the Last Mile in Smallholder Precision Agriculture with Conversational IoT}, 
  author={Muhammad Saad Ali and Daanish U. Khan and Laiba Intizar Ahmad and Umer Irfan and Maryam Mustafa and Naveed Anwar Bhatti and Muhammad Hamad Alizai},
  year={2026},
  eprint={2602.08593},
  archivePrefix={arXiv},
  primaryClass={cs.HC},
  url={https://arxiv.org/abs/2602.08593}
}
```

---

## 🏷️ Standard Citation Formats

### APA Format
* Casheekar, A., Lahiri, A., Rath, K., Prabhakar, K. S., & Srinivasan, K. (2024). A contemporary review on chatbots, AI-powered virtual conversational agents, ChatGPT: Applications, open challenges and future research directions. *Computer Science Review*, *52*, 100632. https://doi.org/10.1016/j.cosrev.2024.100632
* Ali, M. S., Khan, D. U., Ahmad, L. I., Irfan, U., Mustafa, M., Bhatti, N. A., & Alizai, M. H. (2026). *Kissan-Dost: Bridging the last mile in smallholder precision agriculture with conversational IoT*. arXiv preprint arXiv:2602.08593. https://arxiv.org/abs/2602.08593

### IEEE Format
* [1] A. Casheekar, A. Lahiri, K. Rath, K. S. Prabhakar, and K. Srinivasan, "A contemporary review on chatbots, AI-powered virtual conversational agents, ChatGPT: Applications, open challenges and future research directions," *Computer Science Review*, vol. 52, p. 100632, May 2024, doi: 10.1016/j.cosrev.2024.100632.
* [2] M. S. Ali *et al.*, "Kissan-Dost: Bridging the Last Mile in Smallholder Precision Agriculture with Conversational IoT," Feb. 2026, arXiv:2602.08593. [Online]. Available: https://arxiv.org/abs/2602.08593.

---

## 🤝 Acknowledgments

We express our gratitude to the authors and academic communities behind these research publications for making their methodologies, surveys, and insights available to the broader artificial intelligence and human-computer interaction research ecosystem.
