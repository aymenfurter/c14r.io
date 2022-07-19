ARG VARIANT="17-bullseye"
FROM mcr.microsoft.com/vscode/devcontainers/java:0-${VARIANT}

# maven
RUN su vscode -c "umask 0002 && . /usr/local/sdkman/bin/sdkman-init.sh && sdk install maven \"${MAVEN_VERSION}\""

# install google chrome / Selenium
RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add -
RUN sh -c 'echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list'
RUN apt-get -y update
RUN apt-get install -y google-chrome-stable
RUN apt-get install -yqq unzip
RUN wget -O /tmp/chromedriver.zip http://chromedriver.storage.googleapis.com/`curl -sS chromedriver.storage.googleapis.com/LATEST_RELEASE`/chromedriver_linux64.zip
RUN unzip /tmp/chromedriver.zip chromedriver -d /usr/local/bin/
ENV DISPLAY=:99
RUN apt install -y python3-pip
RUN pip install --upgrade pip
RUN pip install selenium

# NeoVIM Setup
RUN apt-get update && apt-get install -y fuse
RUN wget -O /home/vscode/nvim.appimage https://github.com/neovim/neovim/releases/download/v0.6.1/nvim.appimage
RUN chmod +x /home/vscode/nvim.appimage
RUN wget -O /home/vscode/.vimrc https://raw.githubusercontent.com/aymenfurter/dotfiles-ipados/main/.vimrc
RUN mkdir -p /home/vscode/.config/nvim && wget -O /home/vscode/.config/nvim/init.vim https://raw.githubusercontent.com/aymenfurter/dotfiles-ipados/main/.vimrc
RUN ln -s /home/vscode/nvim.appimage /usr/bin/nvim
RUN ln -s /home/vscode/nvim.appimage /usr/bin/vim
