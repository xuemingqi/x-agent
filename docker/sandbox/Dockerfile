FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    PATH=/opt/agent-python/bin:$PATH \
    PIP_NO_CACHE_DIR=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        file \
        jq \
        libreoffice-nogui \
        poppler-utils \
        python3 \
        python3-pip \
        python3-venv \
        ripgrep \
        tesseract-ocr \
        tesseract-ocr-chi-sim \
        tini \
        unzip \
        wget \
    && rm -rf /var/lib/apt/lists/*

RUN python3 -m venv /opt/agent-python \
    && pip install --upgrade pip setuptools wheel \
    && pip install \
        beautifulsoup4 \
        lxml \
        odfpy \
        openpyxl \
        pandas \
        pdfplumber \
        pyarrow \
        pymupdf \
        pypdf \
        python-docx \
        python-pptx \
        xlrd

WORKDIR /workspace

ENTRYPOINT ["/usr/bin/tini", "--"]
