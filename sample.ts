function timer_one() {
    this.seconds = 0;

    setInterval(function addSecond() {
        this.seconds++;
    }, 1000);
}

function timer_two() {
    this.seconds = 0;

    setInterval(() => {
        this.seconds++;
    }, 1000);
}